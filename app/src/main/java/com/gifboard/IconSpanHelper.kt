package com.gifboard

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat

/**
 * Helper to replace text placeholders with inline icons.
 */
object IconSpanHelper {
    
    /**
     * Replaces [GLOBE] and [KEYBOARD] placeholders with inline icons.
     */
    fun replaceIconPlaceholders(context: Context, text: CharSequence): SpannableString {
        val spannable = SpannableString(text)
        
        replaceWithIcon(context, spannable, "[GLOBE]", R.drawable.ic_language)
        replaceWithIcon(context, spannable, "[KEYBOARD]", R.drawable.ic_ime_switcher)
        
        return spannable
    }
    
    private fun replaceWithIcon(
        context: Context, 
        spannable: SpannableString, 
        placeholder: String, 
        drawableRes: Int
    ) {
        var start = spannable.toString().indexOf(placeholder)
        while (start >= 0) {
            val end = start + placeholder.length
            
            val drawable: Drawable? = ContextCompat.getDrawable(context, drawableRes)
            drawable?.let {
                // Set bounds - make icon inline with text (roughly 1em = 20dp at typical text size)
                val size = (context.resources.displayMetrics.density * 18).toInt()
                it.setBounds(0, 0, size, size)
                it.setTint(context.resources.getColor(R.color.key_text, context.theme))
                
                val imageSpan = ImageSpan(it, ImageSpan.ALIGN_BASELINE)
                spannable.setSpan(imageSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            start = spannable.toString().indexOf(placeholder, end)
        }
    }
}
