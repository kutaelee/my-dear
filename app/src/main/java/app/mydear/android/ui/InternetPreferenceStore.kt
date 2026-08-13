package app.mydear.android.ui

import android.content.Context

class InternetPreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    fun hasSavedChoice(): Boolean = preferences.contains(KEY_ENABLED)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        const val FILE_NAME = "internet_preferences"
        const val KEY_ENABLED = "enabled"
    }
}
