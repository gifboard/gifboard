package com.gifboard

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Fetches GIFs from Google Image Search.
 */
class GoogleGifFetcher {

    companion object {
        private const val BASE_URL = "https://www.google.com/search"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 7 Build/MOB30X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36"
    }

    data class GifSearchRequest(
        val query: String, 
        val pageIndex: Int = 0,
        val safeSearch: String = "active"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchGifs(request: GifSearchRequest): String {
        require(request.query.isNotBlank()) { "Query cannot be empty" }

        val params = mapOf(
            "q" to "${request.query} gif",
            "tbm" to "isch", // "to be matched = image search"
            "tbs" to "itp:animated", // "to be searched = image type: animated gifs"
            "client" to "chrome",
            "safe" to request.safeSearch,
            "asearch" to "isch",
            "async" to "ijn:${request.pageIndex},_fmt:json"
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8.toString())}=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString())}"
        }

        val url = "$BASE_URL?$queryString"

        val httpRequest = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = client.newCall(httpRequest).execute()
        var content = response.body?.string() ?: ""

        // Strip security prefix
        if (content.startsWith(")]}'")) {
            content = content.substring(4).trim()
        }

        return content
    }
}
