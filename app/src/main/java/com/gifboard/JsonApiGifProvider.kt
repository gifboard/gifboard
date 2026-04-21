package com.gifboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Legacy GIF provider that fetches results via Google's undocumented JSON API.
 * Uses a direct OkHttp request with `async=ijn:<page>,_fmt:json` to get
 * structured JSON responses. Lightweight but may not always be available.
 */
class JsonApiGifProvider : GifProvider {

    companion object {
        private const val BASE_URL = "https://www.google.com/search"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
        private const val MAX_FILE_SIZE_MB = 10.0f
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun search(
        query: String,
        page: Int,
        safeSearch: String,
        timeoutMs: Long
    ): List<GifItem> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Query cannot be empty" }

        val params = mapOf(
            "q" to "$query gif",
            "tbm" to "isch",
            "tbs" to "itp:animated",
            "client" to "chrome",
            "safe" to safeSearch,
            "asearch" to "isch",
            "async" to "ijn:$page,_fmt:json"
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8.toString())}=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString())}"
        }

        val httpRequest = Request.Builder()
            .url("$BASE_URL?$queryString")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = client.newCall(httpRequest).execute()
        var content = response.body?.string() ?: ""

        // Strip Google's XSSI protection prefix
        if (content.startsWith(")]}'")) {
            content = content.substring(4).trim()
        }

        parseJsonResults(content)
    }

    private fun parseJsonResults(jsonResponse: String): List<GifItem> {
        val items = mutableListOf<GifItem>()
        try {
            val json = JSONObject(jsonResponse)
            val ischj = json.optJSONObject("ischj") ?: return items
            val resultsStr = ischj.optString("results")
            val results = JSONArray(resultsStr)

            for (i in 0 until results.length()) {
                val gif = results.getJSONObject(i)

                // Skip files larger than 10 MB
                val sizeStr = gif.optString("os")
                if (isSizeTooLarge(sizeStr)) continue

                val url = gif.optString("ou")
                val thumbnailUrl = gif.optString("tu").takeIf { it.isNotEmpty() }
                val width = gif.optInt("ow", 200)
                val height = gif.optInt("oh", 200)

                if (url.isNotEmpty() && width > 0 && height > 0) {
                    items.add(GifItem(url, thumbnailUrl, width, height))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun isSizeTooLarge(sizeStr: String): Boolean {
        if (sizeStr.isEmpty()) return false
        try {
            val upperStr = sizeStr.uppercase(java.util.Locale.US)
            val value = upperStr.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: return false
            return upperStr.endsWith("MB") && value > MAX_FILE_SIZE_MB
        } catch (e: Exception) {
            return false
        }
    }
}
