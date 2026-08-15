package app.mydear.android.runtime.litert

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.ConversationEvent
import app.mydear.android.domain.ConversationRequest
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.ModelBackend
import app.mydear.android.domain.Role
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Ignore

@Ignore("E4B production artifact requires a physical LiteRT GPU; x86_64 AVD CPU reached 5.88 GB RSS and was LMK-killed")
class LiteRtGemmaE4BAvdSmokeTest {
    @Test fun streamsARealTokenFromPinnedGemma4E4B() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.filesDir.resolve("qa/gemma-4-E4B-it-gpu.litertlm")
        assumeTrue("AVD Gemma E4B smoke model was not staged", model.isFile)
        val engine = LiteRtConversationEngine(context.cacheDir.resolve("litert-gemma-e4b-smoke"))
        try {
            withTimeout(180_000) {
                engine.prepare(InstalledModel("gemma-4-e4b-it-mobile", model.absolutePath, "qa-staged", ModelBackend.Gpu))
                val firstText = engine.stream(
                    ConversationRequest(
                        turnId = TurnId("gemma-e4b-avd-smoke"),
                        messages = listOf(ChatMessage("q", Role.User, "어르신께 공손하게 한 문장으로 인사해 주세요.")),
                    ),
                ).filterIsInstance<ConversationEvent.TextDelta>().first { it.value.isNotBlank() }
                assertTrue(firstText.value.isNotBlank())
            }
        } finally {
            engine.release()
        }
    }
}
