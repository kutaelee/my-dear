package app.mydear.android.memory

import app.mydear.android.domain.ChatMessage
import app.mydear.android.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryContextBoundaryTest {
    @Test fun deletedMemoryHistoryIsExcludedFromFutureInference() {
        val messages = listOf(
            ChatMessage("remember-user", Role.User, "내 이름은 민수야 기억해줘"),
            ChatMessage("remember-answer", Role.Assistant, "기억해둘게요"),
            ChatMessage("forget-user", Role.User, "내 이름 잊어줘"),
            ChatMessage("forget-answer", Role.Assistant, "기억을 지웠어요"),
            ChatMessage("new-user", Role.User, "내 이름이 뭐야?"),
        )

        assertEquals(listOf("new-user"), messagesAfterMemoryBoundary(messages, "forget-answer").map { it.id })
        assertEquals(emptyList<ChatMessage>(), messagesAfterMemoryBoundary(messages, "expired-boundary"))
    }

    @Test fun latestOfMultiplePrivacyBoundariesWins() {
        val messages = listOf(
            ChatMessage("before", Role.User, "공유 전 질문"),
            ChatMessage("screen-answer", Role.Assistant, "공유 화면 답변"),
            ChatMessage("after", Role.User, "공유 종료 후 질문"),
        )

        assertEquals(
            listOf("after"),
            messagesAfterBoundaries(messages, listOf("before", "screen-answer")).map { it.id },
        )
    }

    @Test fun missingPrivacyBoundaryFailsClosed() {
        val messages = listOf(
            ChatMessage("screen-answer", Role.Assistant, "공유 화면 답변"),
            ChatMessage("after", Role.User, "공유 종료 후 질문"),
        )

        assertEquals(emptyList<ChatMessage>(), messagesAfterBoundaries(messages, listOf("missing", "screen-answer")))
    }

    @Test fun assistantPlaceholderBoundaryExcludesPartialScreenAnswerAfterProcessDeath() {
        val messages = listOf(
            ChatMessage("screen-user", Role.User, "이 화면에서 어디를 눌러?"),
            ChatMessage("screen-answer", Role.Assistant, "오른쪽 아래의 설"),
            ChatMessage("after-restart", Role.User, "새 질문"),
        )

        assertEquals(
            listOf("after-restart"),
            messagesAfterBoundaries(messages, listOf("screen-answer")).map { it.id },
        )
    }
}
