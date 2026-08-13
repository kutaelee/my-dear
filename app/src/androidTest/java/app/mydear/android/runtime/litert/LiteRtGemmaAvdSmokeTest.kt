package app.mydear.android.runtime.litert

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.ConversationEvent
import app.mydear.android.domain.ConversationRequest
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.Role
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiteRtGemmaAvdSmokeTest {
    @Test fun streamsARealTokenFromPinnedGemma4E2B() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.filesDir.resolve("qa/gemma-4-E2B-it.litertlm")
        assumeTrue("AVD Gemma smoke model was not staged", model.isFile)
        val engine = LiteRtConversationEngine(context.cacheDir.resolve("litert-gemma-smoke"))
        try {
            withTimeout(150_000) {
                engine.prepare(InstalledModel("gemma-4-e2b-it-mobile", model.absolutePath, "qa-staged"))
                val firstText = engine.stream(
                    ConversationRequest(
                        turnId = TurnId("gemma-avd-smoke"),
                        messages = listOf(ChatMessage("q", Role.User, "한 단어로 인사해 주세요.")),
                    ),
                ).filterIsInstance<ConversationEvent.TextDelta>().first { it.value.isNotBlank() }
                assertTrue(firstText.value.isNotBlank())
            }
        } finally {
            engine.release()
        }
    }
}
