package com.gifboard

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetches and parses GIFs from Google Image Search using an invisible WebView.
 * Owns the full pipeline: fetch HTML → detect readiness → parse results.
 */
class GoogleGifFetcher(private val webView: WebView) : GifProvider {

    companion object {
        private const val BASE_URL = "https://www.google.com/search"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

        // Shared regex: used for both polling detection and result parsing
        // Flexible to extra metadata: [2, "id", ["thumbnail", h, w], ["full", h, w]]
        val GIF_DATA_PATTERN = Regex("""\[\d+,\s*"[^"]*",\s*\["([^"]+)",\s*\d+,\s*\d+[^\]]*\],\s*\["([^"]+)",\s*(\d+),\s*(\d+)[^\]]*\]""")
        
        // Machine-readable "About 0 results" or the specific semantic text in the botstuff container.
        // We use curly quotes [’‘] and ensure we're not matching CSS by avoiding bare class names.
        val EMPTY_STATE_PATTERN = Regex("(?i)(\"About 0 results\"|id=\"botstuff\".*?It looks like there aren['’‘]t any|did not match any image results)")
        private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

        private fun unescapeUnicode(s: String): String = UNICODE_ESCAPE.replace(s) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }

        /**
         * Parses GIF metadata from Google Image Search HTML.
         * Extracts thumbnail/full URLs, dimensions, and filters for .gif files.
         * Deduplicates by full URL.
         */

        fun parseGifs(htmlResponse: String): List<GifItem> {
            val items = mutableListOf<GifItem>()
            val seenUrls = mutableSetOf<String>()
            try {
                val matches = GIF_DATA_PATTERN.findAll(htmlResponse)

                for (match in matches) {
                    val (thumbnailUrlEscaped, fullUrlEscaped, heightStr, widthStr) = match.destructured

                    val thumbnailUrl = unescapeUnicode(thumbnailUrlEscaped)
                    val fullUrl = unescapeUnicode(fullUrlEscaped)

                    if (seenUrls.contains(fullUrl)) continue

                    val width = widthStr.toIntOrNull() ?: 200
                    val height = heightStr.toIntOrNull() ?: 200

                    // Filter for gifs and valid sizes
                    if (fullUrl.endsWith(".gif", ignoreCase = true) && width > 0 && height > 0) {
                        items.add(GifItem(fullUrl, thumbnailUrl, width, height))
                        seenUrls.add(fullUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return items
        }
    }

    override suspend fun search(
        query: String,
        page: Int,
        safeSearch: String,
        timeoutMs: Long
    ): List<GifItem> = suspendCancellableCoroutine { cont ->
        require(query.isNotBlank()) { "Query cannot be empty" }

        val params = mutableMapOf(
            "q" to "$query gif",
            "udm" to "2",
            "tbs" to "itp:animated",
            "safe" to safeSearch,
            "gl" to "US",
            "hl" to "en"
        )

        if (page > 0) {
            params["start"] = (page * 20).toString()
        }

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8.toString())}=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString())}"
        }

        webView.settings.userAgentString = USER_AGENT
        
        val url = "$BASE_URL?$queryString"
        var isFinished = false

        val timeoutRunnable = Runnable {
            if (!isFinished && cont.isActive) {
                isFinished = true
                cont.resume(emptyList())
            }
        }
        webView.postDelayed(timeoutRunnable, timeoutMs)

        webView.settings.userAgentString = USER_AGENT

        val pollRunnable = object : Runnable {
            override fun run() {
                if (isFinished || !cont.isActive) return

                webView.evaluateJavascript("(function() { return document.documentElement.innerHTML; })();") { htmlResult ->
                    if (isFinished || !cont.isActive) return@evaluateJavascript

                    val html = try {
                        JSONTokener(htmlResult).nextValue() as? String ?: htmlResult
                    } catch (e: Exception) {
                        htmlResult
                    }

                    val isGifDataMatched = GIF_DATA_PATTERN.containsMatchIn(html)
                    val isEmptyStateMatched = EMPTY_STATE_PATTERN.containsMatchIn(html)

                    // Unified Regex check: Success vs Early Exit
                    when {
                        isGifDataMatched -> {
                            isFinished = true
                            webView.removeCallbacks(timeoutRunnable)
                            cont.resume(parseGifs(html))
                        }
                        isEmptyStateMatched -> {
                            isFinished = true
                            webView.removeCallbacks(timeoutRunnable)
                            cont.resume(emptyList())
                        }
                        else -> {
                            webView.postDelayed(this, 300) // Poll every 300ms
                        }
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript("""
                    (function() {
                        window.dataLayer = window.dataLayer || [];
                        function gtag(){dataLayer.push(arguments);}
                        gtag('consent', 'default', {
                          'ad_storage': 'denied',
                          'ad_user_data': 'denied',
                          'ad_personalization': 'denied',
                          'analytics_storage': 'denied'
                        });
                    })();
                """.trimIndent(), null)
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                if (isFinished || view == null) return

                val currentUrl = finishedUrl ?: ""
                if (currentUrl.contains("google.com/search")) {
                    view.removeCallbacks(pollRunnable)
                    view.post(pollRunnable)
                }
            }

            override fun onReceivedError(view: WebView?, webReq: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, webReq, error)
                if (webReq?.isForMainFrame == true) {
                    if (!isFinished) {
                        isFinished = true
                        webView.removeCallbacks(timeoutRunnable)
                        webView.removeCallbacks(pollRunnable)
                        if (cont.isActive) cont.resumeWithException(Exception("WebView error: ${error?.description}"))
                    }
                }
            }
        }

        cont.invokeOnCancellation {
            webView.removeCallbacks(timeoutRunnable)
            webView.removeCallbacks(pollRunnable)
            webView.stopLoading()
        }

        webView.loadUrl(url)
    }
}
