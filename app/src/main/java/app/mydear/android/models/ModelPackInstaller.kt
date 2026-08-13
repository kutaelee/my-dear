package app.mydear.android.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

@Serializable
data class SignedModelManifest(
    val payloadBase64: String,
    val signatureBase64: String,
)

@Serializable
data class ModelManifestPayload(
    val schemaVersion: Int,
    val id: String,
    val revision: String,
    val format: String,
    val quantization: String,
    val minimumAppVersionCode: Int,
    val files: List<ModelFileEntry>,
)

@Serializable
data class ModelFileEntry(val path: String, val size: Long, val sha256: String)
data class VerifiedModelPack(val id: String, val revision: String, val directory: File)

class ModelPackInstaller(
    private val modelRoot: File,
    private val manifestPublicKey: PublicKey,
    private val currentAppVersionCode: Int,
) {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }

    fun verifyAndActivate(stagingDirectory: File, signedManifestFile: File): VerifiedModelPack {
        require(stagingDirectory.isDirectory && isDirectChild(stagingDirectory, modelRoot.resolve("staging"))) {
            "허용되지 않은 모델 임시 경로예요"
        }
        val envelope = json.decodeFromString<SignedModelManifest>(signedManifestFile.readText())
        val payloadBytes = Base64.getDecoder().decode(envelope.payloadBase64)
        val signatureBytes = Base64.getDecoder().decode(envelope.signatureBase64)
        check(verifySignature(payloadBytes, signatureBytes)) { "모델 서명을 확인할 수 없어요" }
        val manifest = json.decodeFromString<ModelManifestPayload>(payloadBytes.decodeToString())
        validateManifest(manifest)
        verifyFiles(stagingDirectory, manifest)

        val installedRoot = modelRoot.resolve("installed").apply { mkdirs() }.canonicalFile
        val target = installedRoot.resolve("${manifest.id}-${manifest.revision}").canonicalFile
        check(target.parentFile == installedRoot && !target.exists()) { "이미 설치되었거나 잘못된 모델 경로예요" }
        Files.move(stagingDirectory.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)

        val active = modelRoot.resolve("active-${safeId(manifest.id)}.txt")
        val pending = modelRoot.resolve(".${active.name}.new")
        pending.writeText(target.name)
        Files.move(pending.toPath(), active.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return VerifiedModelPack(manifest.id, manifest.revision, target)
    }

    private fun validateManifest(manifest: ModelManifestPayload) {
        check(manifest.schemaVersion == 1) { "지원하지 않는 모델 설명서예요" }
        check(manifest.id == safeId(manifest.id) && manifest.revision == safeId(manifest.revision)) { "모델 이름이 올바르지 않아요" }
        check(manifest.minimumAppVersionCode <= currentAppVersionCode) { "앱 업데이트가 먼저 필요해요" }
        check(manifest.files.isNotEmpty() && manifest.files.size <= MAX_FILES) { "모델 파일 목록이 올바르지 않아요" }
        check(manifest.files.sumOf { it.size } <= MAX_PACK_BYTES) { "모델이 허용 크기보다 커요" }
        check(manifest.files.map { it.path }.distinct().size == manifest.files.size) { "중복된 모델 파일이 있어요" }
    }

    private fun verifyFiles(staging: File, manifest: ModelManifestPayload) {
        val stagingRoot = staging.canonicalFile
        val actualFiles = staging.walkTopDown().filter(File::isFile).filter { it.name != "manifest.json" }.toList()
        check(actualFiles.size == manifest.files.size) { "예상하지 못한 모델 파일이 있어요" }
        manifest.files.forEach { entry ->
            check(isSafeRelativePath(entry.path) && entry.size in 1..MAX_FILE_BYTES) { "모델 파일 경로 또는 크기가 올바르지 않아요" }
            val file = stagingRoot.resolve(entry.path)
            check(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile.toPath().startsWith(stagingRoot.toPath())) { "모델 링크 또는 경로가 올바르지 않아요" }
            check(file.isFile && file.length() == entry.size) { "모델 파일 크기가 달라요" }
            check(file.sha256().equals(entry.sha256, ignoreCase = true)) { "모델 파일이 손상되었어요" }
        }
    }

    private fun verifySignature(payload: ByteArray, signature: ByteArray): Boolean =
        Signature.getInstance("Ed25519").run {
            initVerify(manifestPublicKey)
            update(payload)
            verify(signature)
        }

    private fun isDirectChild(file: File, parent: File): Boolean =
        runCatching { file.canonicalFile.parentFile == parent.canonicalFile }.getOrDefault(false)

    private fun isSafeRelativePath(value: String): Boolean =
        value.isNotBlank() && value.length <= 160 && !value.startsWith("/") && !value.startsWith("\\") &&
            !value.contains("..") && !value.contains(':') && value.all { it.code in 32..126 }

    private fun safeId(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80)

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_FILES = 32
        const val MAX_FILE_BYTES = 4L * 1024 * 1024 * 1024
        const val MAX_PACK_BYTES = 5L * 1024 * 1024 * 1024
    }
}
