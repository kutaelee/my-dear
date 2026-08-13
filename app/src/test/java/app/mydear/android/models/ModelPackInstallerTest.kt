package app.mydear.android.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

class ModelPackInstallerTest {
    private val json = Json { explicitNulls = false }

    @Test fun `valid signed pack activates atomically`() {
        val root = createTempDir("model-pack-valid")
        try {
            val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val staging = root.resolve("staging/pack").apply { mkdirs() }
            val model = staging.resolve("model.litertlm").apply { writeBytes("safe-model".encodeToByteArray()) }
            val manifest = signedManifest(
                ModelManifestPayload(1, "gemma-mobile", "abc123", "litertlm", "mixed-2-4-8", 1, listOf(ModelFileEntry("model.litertlm", model.length(), model.sha256()))),
                pair.private,
            )
            val manifestFile = root.resolve("manifest.json").apply { writeText(manifest) }
            val result = ModelPackInstaller(root, pair.public, 1).verifyAndActivate(staging, manifestFile)
            assertEquals("gemma-mobile", result.id)
            assertEquals("safe-model", result.directory.resolve("model.litertlm").readText())
            assertEquals(result.directory.name, root.resolve("active-gemma-mobile.txt").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun `tampered pack never activates`() {
        val root = createTempDir("model-pack-tampered")
        try {
            val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val staging = root.resolve("staging/pack").apply { mkdirs() }
            val model = staging.resolve("model.onnx").apply { writeBytes("expected".encodeToByteArray()) }
            val payload = ModelManifestPayload(1, "tts", "rev1", "onnx", "int8", 1, listOf(ModelFileEntry("model.onnx", model.length(), model.sha256())))
            val manifestFile = root.resolve("manifest.json").apply { writeText(signedManifest(payload, pair.private)) }
            model.writeText("tampered")
            assertThrows(IllegalStateException::class.java) { ModelPackInstaller(root, pair.public, 1).verifyAndActivate(staging, manifestFile) }
            assertFalse(root.resolve("active-tts.txt").exists())
            assertFalse(root.resolve("installed/tts-rev1").exists())
        } finally { root.deleteRecursively() }
    }

    private fun signedManifest(payload: ModelManifestPayload, privateKey: java.security.PrivateKey): String {
        val bytes = json.encodeToString(payload).encodeToByteArray()
        val signature = Signature.getInstance("Ed25519").run { initSign(privateKey); update(bytes); sign() }
        return json.encodeToString(SignedModelManifest(Base64.getEncoder().encodeToString(bytes), Base64.getEncoder().encodeToString(signature)))
    }

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }
    private fun createTempDir(prefix: String): File = java.nio.file.Files.createTempDirectory(prefix).toFile()
}
