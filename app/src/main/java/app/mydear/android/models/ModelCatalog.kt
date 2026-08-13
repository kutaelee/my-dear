package app.mydear.android.models

import android.content.Context
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.ModelBackend
import java.io.File

enum class GemmaTier(val label: String, val shortDescription: String) {
    E2B("기본 AI", "일상 대화에 알맞아요 · 약 2.6GB"),
    E4B("고급 AI", "더 자세한 답변 · 약 3GB · 고성능 휴대폰용"),
}

data class GemmaArtifact(
    val tier: GemmaTier,
    val id: String,
    val revision: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val backend: ModelBackend,
)

object ModelCatalog {
    val e2b = GemmaArtifact(
        tier = GemmaTier.E2B,
        id = "gemma-4-e2b-it-mobile",
        revision = "6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94",
        fileName = "gemma-4-E2B-it.litertlm",
        bytes = 2_588_147_712,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94/gemma-4-E2B-it.litertlm?download=true",
        backend = ModelBackend.Cpu,
    )
    val e4b = GemmaArtifact(
        tier = GemmaTier.E4B,
        id = "gemma-4-e4b-it-mobile",
        revision = "2eee7ac325f20eb8c9ac1d0e972f7c84663062da",
        fileName = "gemma-4-E4B-it-gpu.litertlm",
        bytes = 2_969_059_328,
        sha256 = "4912bb5a9c30993c51a7711f763212077458529312175df0573a78323a2bb7ff",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it-gpu.litertlm?download=true",
        backend = ModelBackend.Gpu,
    )

    fun artifact(tier: GemmaTier): GemmaArtifact = if (tier == GemmaTier.E2B) e2b else e4b
}

class InstalledModelResolver(private val context: Context) {
    private val modelRoot = context.filesDir.resolve("models")

    fun resolveGemma(tier: GemmaTier): InstalledModel? {
        val artifact = ModelCatalog.artifact(tier)
        val activePointer = modelRoot.resolve("active-${artifact.id}.txt")
        val installed = runCatching {
            val directoryName = activePointer.takeIf(File::isFile)?.readText()?.trim().orEmpty()
            modelRoot.resolve("installed").resolve(directoryName).resolve(artifact.fileName).canonicalFile
        }.getOrNull()
        val qa = context.filesDir.resolve("qa/${artifact.fileName}")
        val selected = installed?.takeIf(File::isFile) ?: qa.takeIf(File::isFile) ?: return null
        return InstalledModel(artifact.id, selected.absolutePath, artifact.sha256, artifact.backend)
    }

    fun resolveSupertonic(): InstalledModel? {
        val id = "supertonic-3-int8-ko"
        val activePointer = modelRoot.resolve("active-$id.txt")
        val installed = runCatching {
            val directoryName = activePointer.takeIf(File::isFile)?.readText()?.trim().orEmpty()
            modelRoot.resolve("installed").resolve(directoryName).canonicalFile
        }.getOrNull()
        val qa = context.filesDir.resolve("qa/supertonic-3-int8")
        val selected = installed?.takeIf { it.resolve("voice.bin").isFile }
            ?: qa.takeIf { it.resolve("voice.bin").isFile }
            ?: return null
        return InstalledModel(id, selected.absolutePath, "verified-pack")
    }
}

class ModelPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("model_preferences", Context.MODE_PRIVATE)
    fun selectedTier(): GemmaTier = runCatching {
        GemmaTier.valueOf(preferences.getString(KEY_TIER, GemmaTier.E2B.name)!!)
    }.getOrDefault(GemmaTier.E2B)
    fun select(tier: GemmaTier) { preferences.edit().putString(KEY_TIER, tier.name).apply() }

    private companion object { const val KEY_TIER = "selected_gemma_tier" }
}
