package app.mydear.android.runtime.litert

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.ConversationEvent
import app.mydear.android.domain.ConversationRequest
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.Role
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiteRtGemmaScreenVisionAvdTest {
    @Test fun readsTheActualSharedScreenImageWithoutOcrText() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val model = context.filesDir.resolve("qa/gemma-4-E2B-it.litertlm")
        assumeTrue("AVD Gemma vision model was not staged", model.isFile)
        val imageBytes = instrumentation.context.assets.open("screen-share-vision-fixture.png").use { it.readBytes() }
        val engine = LiteRtConversationEngine(context.cacheDir.resolve("litert-qa-e2b-shared"))
        try {
            val answer = withTimeout(180_000) {
                engine.prepare(InstalledModel("gemma-4-e2b-it-mobile", model.absolutePath, "qa-staged"))
                val output = StringBuilder()
                engine.stream(
                    ConversationRequest(
                        turnId = TurnId("gemma-screen-vision"),
                        messages = listOf(
                            ChatMessage(
                                "q",
                                Role.User,
                                "이 스마트폰 화면 오른쪽 아래의 톱니바퀴 탭에 적힌 두 글자만 답하세요.",
                            ),
                        ),
                        screenImage = imageBytes,
                    ),
                ).collect { event ->
                    if (event is ConversationEvent.TextDelta) output.append(event.value)
                    if (event is ConversationEvent.Failure) error(event.userMessage)
                }
                output.toString()
            }
            assertTrue("Gemma vision answer should identify 설정, but was: $answer", answer.contains("설정"))
        } finally {
            engine.release()
        }
    }

    @Test fun readsTheUnderlyingAppWhileAssistantIsInPictureInPicture() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val model = context.filesDir.resolve("qa/gemma-4-E2B-it.litertlm")
        assumeTrue("AVD Gemma vision model was not staged", model.isFile)
        val imageBytes = instrumentation.context.assets.open("screen-share-pip-settings-fixture.png").use { it.readBytes() }
        val engine = LiteRtConversationEngine(context.cacheDir.resolve("litert-qa-e2b-shared"))
        try {
            val answer = withTimeout(180_000) {
                engine.prepare(InstalledModel("gemma-4-e2b-it-mobile", model.absolutePath, "qa-staged"))
                val output = StringBuilder()
                engine.stream(
                    ConversationRequest(
                        turnId = TurnId("gemma-screen-pip"),
                        messages = listOf(
                            ChatMessage(
                                "q-pip",
                                Role.User,
                                "이 설정 화면에서 와이파이 메뉴를 열려면 눌러야 할 항목 이름만 답하세요.",
                            ),
                        ),
                        screenImage = imageBytes,
                    ),
                ).collect { event ->
                    if (event is ConversationEvent.TextDelta) output.append(event.value)
                    if (event is ConversationEvent.Failure) error(event.userMessage)
                }
                output.toString()
            }
            assertTrue(
                "Gemma should read the app under the small assistant window, but was: $answer",
                answer.contains("Network", ignoreCase = true) || answer.contains("네트워크"),
            )
        } finally {
            engine.release()
        }
    }
}
