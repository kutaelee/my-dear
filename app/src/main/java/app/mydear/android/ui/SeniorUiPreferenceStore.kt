package app.mydear.android.ui

import android.content.Context

class SeniorUiPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    fun largeText(): Boolean = preferences.getBoolean(KEY_LARGE_TEXT, true)
    fun setLargeText(enabled: Boolean) { preferences.edit().putBoolean(KEY_LARGE_TEXT, enabled).apply() }

    companion object {
        const val FILE_NAME = "senior_ui_preferences"
        private const val KEY_LARGE_TEXT = "large_text"
    }
}
