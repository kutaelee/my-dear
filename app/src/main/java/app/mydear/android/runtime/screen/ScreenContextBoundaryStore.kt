package app.mydear.android.runtime.screen

import android.content.Context

class ScreenContextBoundaryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun messageId(): String? = preferences.getString(KEY_MESSAGE_ID, null)?.takeIf { it.isNotBlank() }

    fun markAfter(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        return preferences.edit().putString(KEY_MESSAGE_ID, messageId.take(80)).commit()
    }

    companion object {
        const val FILE_NAME = "screen_context_boundary"
        private const val KEY_MESSAGE_ID = "after_message_id"
    }
}
