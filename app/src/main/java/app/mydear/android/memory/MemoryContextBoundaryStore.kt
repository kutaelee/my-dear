package app.mydear.android.memory

import android.content.Context
import app.mydear.android.domain.ChatMessage

class MemoryContextBoundaryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun messageId(): String? = preferences.getString(KEY_MESSAGE_ID, null)?.takeIf { it.isNotBlank() }

    fun markAfter(messageId: String?) {
        preferences.edit().apply {
            if (messageId.isNullOrBlank()) remove(KEY_MESSAGE_ID) else putString(KEY_MESSAGE_ID, messageId.take(80))
        }.apply()
    }

    companion object {
        const val FILE_NAME = "memory_context_boundary"
        private const val KEY_MESSAGE_ID = "after_message_id"
    }
}

internal fun messagesAfterMemoryBoundary(messages: List<ChatMessage>, boundaryMessageId: String?): List<ChatMessage> {
    if (boundaryMessageId.isNullOrBlank()) return messages
    val boundaryIndex = messages.indexOfLast { it.id == boundaryMessageId }
    return if (boundaryIndex >= 0) messages.drop(boundaryIndex + 1) else emptyList()
}
