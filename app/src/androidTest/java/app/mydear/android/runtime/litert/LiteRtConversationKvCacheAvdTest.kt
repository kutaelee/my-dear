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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiteRtConversationKvCacheAvdTest {
    @Test fun secondTurnReusesConversationKvCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.filesDir.resolve("qa/gemma-4-E2B-it.litertlm")
        assumeTrue("Stage the pinned QA model first", model.isFile)
        val engine = LiteRtConversationEngine(context.cacheDir.resolve("litert-kv-cache"))
        try {
            engine.prepare(InstalledModel("qa-e2b", model.absolutePath, "qa", ModelBackend.Cpu))
            val firstUser = ChatMessage("kv-user-1", Role.User, "이번 대화에서 암호는 바다별이라고 기억해 주세요. 짧게 확인만 해주세요.")
            val firstAnswer = engine.answer(ConversationRequest(TurnId("kv-1"), listOf(firstUser)))
            val firstTokenCount = engine.cachedTokenCountForTest()
            val secondUser = ChatMessage("kv-user-2", Role.User, "방금 정한 암호가 무엇인지 한 단어로 답하세요.")
            val secondAnswer = engine.answer(
                ConversationRequest(
                    TurnId("kv-2"),
                    listOf(firstUser, ChatMessage("kv-assistant-1", Role.Assistant, firstAnswer), secondUser),
                ),
            )
            val secondTokenCount = engine.cachedTokenCountForTest()
            assertTrue("두 번째 턴이 첫 KV 캐시 뒤에 누적되어야 합니다: $firstTokenCount -> $secondTokenCount", secondTokenCount > firstTokenCount)
            assertTrue("이전 턴의 암호를 기억해야 합니다: $secondAnswer", secondAnswer.contains("바다별"))
            assertTrue("질문을 그대로 반복하면 안 됩니다: $secondAnswer", !secondAnswer.contains("무엇인지"))
        } finally {
            engine.release()
        }
    }

    private suspend fun LiteRtConversationEngine.answer(request: ConversationRequest): String =
        stream(request).filterIsInstance<ConversationEvent.TextDelta>().toList().joinToString("") { it.value }
}
