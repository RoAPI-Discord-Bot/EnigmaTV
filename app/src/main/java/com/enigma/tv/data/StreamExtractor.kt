package com.enigma.tv.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Hidden WebView extractor: loads embed pages and captures direct HLS/MP4 URLs
 * from network traffic and DOM — no visible embed player for the user.
 */
class StreamExtractor(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractStreamUrl(embedUrl: String, referer: String? = null): String? =
        withTimeoutOrNull(22_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val finished = AtomicBoolean(false)
                    val refererHeader = referer ?: embedUrl
                    val handler = Handler(Looper.getMainLooper())
                    var webView: WebView? = null

                    fun complete(url: String?) {
                        if (finished.compareAndSet(false, true)) {
                            handler.removeCallbacksAndMessages(null)
                            webView?.destroy()
                            webView = null
                            cont.resume(url)
                        }
                    }

                    webView = WebView(context.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = USER_AGENT

                        addJavascriptInterface(
                            JsBridge { url -> complete(url) },
                            "EnigmaStream"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val url = request.url.toString()
                                pickStreamUrl(url)?.let { complete(it) }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript(EXTRACT_JS, null)
                            }
                        }
                        loadUrl(embedUrl, mapOf("Referer" to refererHeader))
                    }

                    handler.postDelayed({ complete(null) }, 21_000)

                    cont.invokeOnCancellation {
                        handler.post {
                            webView?.destroy()
                            webView = null
                        }
                    }
                }
            }
        }

    private fun pickStreamUrl(url: String): String? {
        val lower = url.lowercase()
        if (lower.contains(".m3u8")) return cleanUrl(url)
        if (lower.contains(".mp4") && !lower.contains("thumbnail") && !lower.contains("preview")) {
            return cleanUrl(url)
        }
        return null
    }

    private fun cleanUrl(url: String): String =
        url.replace("\\/", "/").replace("\\u0026", "&").trim()

    private class JsBridge(private val onFound: (String) -> Unit) {
        @JavascriptInterface
        fun onStreamFound(url: String) {
            if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
                onFound(url)
            }
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val EXTRACT_JS = """
(function() {
  try {
    var found = [];
    document.querySelectorAll('video, source').forEach(function(el) {
      var s = el.src || el.getAttribute('src');
      if (s && (s.indexOf('.m3u8') >= 0 || s.indexOf('.mp4') >= 0)) found.push(s);
    });
    if (found.length > 0) EnigmaStream.onStreamFound(found[0]);
  } catch(e) {}
})();
"""
    }
}
