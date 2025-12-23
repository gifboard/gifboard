package com.gifboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Helper class to manage tutorial-related preferences.
 */
object TutorialPreferences {
    private const val PREFS_NAME = "gifboard_tutorial"
    private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isTutorialCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TUTORIAL_COMPLETED, false)
    }

    fun setTutorialCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TUTORIAL_COMPLETED, completed).apply()
    }

    fun resetTutorial(context: Context) {
        setTutorialCompleted(context, false)
    }
}
