package com.gifboard

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager

/**
 * Utility class for checking input method status.
 * Based on HeliBoard's UncachedInputMethodManagerUtils.
 */
object InputMethodUtils {

    /**
     * Check if this IME is enabled in system settings.
     */
    fun isThisImeEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val packageName = context.packageName
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    /**
     * Check if this IME is the currently selected input method.
     */
    fun isThisImeCurrent(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imi = getInputMethodInfoOf(context.packageName, imm) ?: return false
        val currentImeId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return imi.id == currentImeId
    }

    private fun getInputMethodInfoOf(packageName: String, imm: InputMethodManager): InputMethodInfo? {
        return imm.inputMethodList.find { it.packageName == packageName }
    }

    /**
     * Get list of enabled IMEs excluding this app.
     * Returns a list of pairs: (IME ID, Display Label)
     */
    fun getOtherEnabledIMEs(context: Context): List<Pair<String, String>> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val myPackage = context.packageName
        return imm.enabledInputMethodList
            .filter { it.packageName != myPackage }
            .map { it.id to it.loadLabel(context.packageManager).toString() }
    }

    /**
     * Get the display name for an IME ID.
     */
    fun getIMEDisplayName(context: Context, imeId: String): String? {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.inputMethodList
            .find { it.id == imeId }
            ?.loadLabel(context.packageManager)
            ?.toString()
    }

    /**
     * Check if an IME ID is still valid (enabled).
     */
    fun isIMEEnabled(context: Context, imeId: String): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.id == imeId }
    }
}
