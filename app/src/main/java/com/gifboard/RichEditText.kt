package com.gifboard

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.google.android.material.textfield.TextInputEditText

/**
 * EditText that can receive rich content (images/GIFs) from IMEs.
 */
class RichEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    companion object {
        private val SUPPORTED_MIME_TYPES = arrayOf(
            "image/gif",
            "image/png",
            "image/jpeg",
            "image/webp"
        )
    }

    /**
     * Callback for when content is received from the keyboard.
     */
    var onContentReceived: ((Uri, Uri?) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null

        // Declare supported MIME types for rich content
        EditorInfoCompat.setContentMimeTypes(outAttrs, SUPPORTED_MIME_TYPES)

        // Create callback for receiving content
        val callback = InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->
            handleCommitContent(inputContentInfo, flags)
        }

        @Suppress("DEPRECATION")
        return InputConnectionCompat.createWrapper(ic, outAttrs, callback)
    }

    private fun handleCommitContent(
        inputContentInfo: InputContentInfoCompat,
        flags: Int
    ): Boolean {
        // Request permission for content if needed (API 25+)
        if (Build.VERSION.SDK_INT >= 25 &&
            (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
        ) {
            try {
                inputContentInfo.requestPermission()
            } catch (e: Exception) {
                return false
            }
        }

        // Get content URI
        val contentUri = inputContentInfo.contentUri
        val linkUri = inputContentInfo.linkUri

        // Notify listener about the received content
        onContentReceived?.invoke(contentUri, linkUri)

        // Release permission after a delay to allow image loading
        postDelayed({
            try {
                inputContentInfo.releasePermission()
            } catch (e: Exception) {
                // Ignore
            }
        }, 5000)

        return true
    }
}
