package com.gifboard

/**
 * Interface for GIF search providers.
 * Implementations fetch GIF results from different backends
 * (e.g., headless WebView scraping or direct JSON API).
 */
interface GifProvider {
    suspend fun search(
        query: String,
        page: Int,
        safeSearch: String,
        timeoutMs: Long
    ): List<GifItem>
}
