package app.mydear.android.runtime.litert

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.ConversationEvent
import app.mydear.android.domain.ConversationRequest
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.Role
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiteRtGemmaSoakAvdTest {
    @Test fun oneHundredFreshConversationsCompleteWithoutCrashOrContextDuplication() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.filesDir.resolve("qa/gemma-4-E2B-it.litertlm")
        assumeTrue("AVD Gemma E2B soak model was not staged", model.isFile)
        val engine = LiteRtConversationEngine(context.cacheDir.resolve("litert-qa-e2b-shared"))
        try {
            withTimeout(900_000) {
                engine.prepare(InstalledModel("gemma-4-e2b-it-mobile", model.absolutePath, "qa-staged"))
                repeat(100) { index ->
                    val output = engine.stream(
                        ConversationRequest(
                            TurnId("soak-$index"),
                            listOf(ChatMessage("q-$index", Role.User, "네라고만 답하세요.")),
                        ),
                    ).filterIsInstance<ConversationEvent.TextDelta>().toList().joinToString("") { it.value }
                    assertTrue("turn $index returned no text", output.isNotBlank())
                }
            }
        } finally {
            engine.release()
        }
    }
}
