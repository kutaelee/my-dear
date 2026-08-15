package app.mydear.android.ui

import android.content.Context

class OnboardingStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = preferences.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() {
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    companion object {
        const val FILE_NAME = "onboarding"
        const val KEY_COMPLETED = "completed"
    }
}
