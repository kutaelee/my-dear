package app.mydear.android.runtime.litert

import androidx.test.platform.app.InstrumentationRegistry
import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.ConversationEvent
import app.mydear.android.domain.ConversationRequest
import app.mydear.android.domain.InstalledModel
import app.mydear.android.domain.Role
import app.mydear.android.domain.SearchDocument
import app.mydear.android.domain.SearchEvidence
import app.mydear.android.domain.TurnId
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class KoreanAnswerQualityGemmaAvdTest {
    @Test fun answersGenericBlanketFoldingWithoutRepeatingClarification() = runBlocking {
        withEngine { engine ->
            val answer = engine.answer(
                ConversationRequest(
                    TurnId("blanket"),
                    listOf(
                        ChatMessage("q1", Role.User, "담요 개는법"),
                        ChatMessage("a1", Role.Assistant, "어떤 종류의 담요인지 알려 주세요."),
                        ChatMessage("q2", Role.User, "아무 담요나"),
                    ),
                ),
            )
            assertTrue("바로 실행할 수 있는 단계가 필요합니다: $answer", Regex("[1-5][.)]|반으로|접").containsMatchIn(answer))
            assertFalse("같은 확인 질문을 반복했습니다: $answer", answer.contains("어떤 종류"))
            assertFalse("추가 설명만 요구했습니다: $answer", answer.contains("자세히 말씀"))
        }
    }

    @Test fun currentPresidentComesFromEvidenceInsteadOfGuessing() = runBlocking {
        withEngine { engine ->
            val answer = engine.answer(
                ConversationRequest(
                    TurnId("current-fact"),
                    listOf(ChatMessage("q", Role.User, "대한민국 대통령이 누구야?")),
                    SearchEvidence(
                        listOf(
                            SearchDocument(
                                "대한민국 대통령 목록",
                                "ko.wikipedia.org",
                                "https://ko.wikipedia.org/wiki/test",
                                "2026년 8월 14일 확인 기준, 현 대한민국 대통령은 이재명이다.",
                                "2026-08-14",
                            ),
                        ),
                    ),
                ),
            )
            assertTrue("검색 자료의 인물을 답해야 합니다: $answer", answer.contains("이재명"))
            assertFalse("보고된 환각 이름이 다시 나왔습니다: $answer", answer.contains("이수환"))
        }
    }

    private suspend fun withEngine(block: suspend (LiteRtConversationEngine) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.filesDir.resolve("qa/gemma-4-E2B-it.litertlm")
        assumeTrue("AVD Gemma E2B model was not staged", model.isFile)
        val engine = LiteRtConversationEngine(context.cacheDir.resolve("litert-qa-e2b-shared"))
        try {
            withTimeout(240_000) {
                engine.prepare(InstalledModel("gemma-4-e2b-it-mobile", model.absolutePath, "qa-staged"))
                block(engine)
            }
        } finally {
            engine.release()
        }
    }

    private suspend fun LiteRtConversationEngine.answer(request: ConversationRequest): String =
        stream(request).filterIsInstance<ConversationEvent.TextDelta>().toList().joinToString("") { it.value }
}
