package app.mydear.android.runtime.litert

import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.ConversationRequest
import app.mydear.android.domain.Role
import app.mydear.android.domain.SearchDocument
import app.mydear.android.domain.SearchEvidence
import app.mydear.android.domain.TurnId
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPromptTest {
    @Test fun simpleHouseholdQuestionMustBeAnsweredDirectly() {
        val prompt = buildConversationPrompt(
            ConversationRequest(
                TurnId("test"),
                listOf(
                    ChatMessage("1", Role.User, "담요 개는법"),
                    ChatMessage("2", Role.Assistant, "어떤 담요인지 알려주세요"),
                    ChatMessage("3", Role.User, "아무 담요나"),
                ),
            ),
        )
        assertTrue(prompt.contains("일반적인 X로 이해"))
        assertTrue(prompt.contains("반복하지 말고 바로 고쳐 답하세요"))
        assertTrue(prompt.contains("3~5단계"))
    }

    @Test fun unstableFactRequiresEvidenceAndCitation() {
        val prompt = buildConversationPrompt(
            ConversationRequest(
                TurnId("test"),
                listOf(ChatMessage("1", Role.User, "대통령이 누구야")),
                SearchEvidence(
                    listOf(SearchDocument("대한민국 대통령 목록", "ko.wikipedia.org", "https://ko.wikipedia.org/wiki/test", "현 대통령 정보", null)),
                ),
            ),
        )
        assertTrue(prompt.contains("이름이나 수치를 절대 추측하지 말고"))
        assertTrue(prompt.contains("반드시 [자료 N]"))
        assertTrue(prompt.contains("<UNTRUSTED_SEARCH_EVIDENCE>"))
    }

    @Test fun longHistoryCannotRemoveCurrentQuestionOrSearchEvidence() {
        val messages = (1..11).map { index -> ChatMessage("$index", Role.Assistant, "이전 대화 ".repeat(300)) } +
            ChatMessage("current", Role.User, "현재 대통령이 누구야")
        val prompt = buildConversationPrompt(
            ConversationRequest(
                TurnId("budget"),
                messages,
                SearchEvidence(listOf(SearchDocument("현재 자료", "example.com", "https://example.com", "현 대통령 근거", null))),
            ),
        )
        assertTrue(prompt.contains("현재 대통령이 누구야"))
        assertTrue(prompt.contains("<UNTRUSTED_SEARCH_EVIDENCE>"))
        assertTrue(prompt.contains("현 대통령 근거"))
        assertTrue(prompt.length <= 16_000)
    }
}
