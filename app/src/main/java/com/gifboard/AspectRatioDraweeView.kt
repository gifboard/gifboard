@file:Suppress("DEPRECATION")
package com.gifboard

import android.content.Context
import android.util.AttributeSet
import com.facebook.drawee.view.SimpleDraweeView

/**
 * SimpleDraweeView that maintains a specific aspect ratio.
 */
class AspectRatioDraweeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SimpleDraweeView(context, attrs, defStyleAttr) {

    fun setGifAspectRatio(ratio: Float) {
        if (ratio > 0) {
            aspectRatio = ratio
        }
    }
}
