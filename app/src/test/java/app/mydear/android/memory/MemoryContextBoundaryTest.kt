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
}
