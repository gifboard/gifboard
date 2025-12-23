package com.gifboard

/**
 * Data class representing a GIF item with its URL and dimensions.
 */
data class GifItem(
    val url: String,
    val thumbnailUrl: String?,
    val width: Int,
    val height: Int
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}
