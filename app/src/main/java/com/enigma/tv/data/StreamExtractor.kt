package com.enigma.tv.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.enigma.tv.util.isTelevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Hidden WebView extractor attached to the activity window (required to avoid native crashes).
 * JS and network callbacks are marshalled to the main thread before completing.
 */
class StreamExtractor(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractStreamUrl(
        embedUrl: String,
        referer: String? = null,
        activity: Activity? = null
    ): String? {
        if (context.isTelevision()) return null
        val hostActivity = activity ?: return null
        return withTimeoutOrNull(15_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val finished = AtomicBoolean(false)
                    val refererHeader = referer ?: embedUrl
                    val handler = Handler(Looper.getMainLooper())
                    var webView: WebView? = null
                    var hostContainer: FrameLayout? = null

                    fun complete(url: String?) {
                        handler.post {
                            if (!finished.compareAndSet(false, true)) return@post
                            handler.removeCallbacksAndMessages(null)
                            safeDestroyWebView(webView, hostContainer)
                            webView = null
                            hostContainer = null
                            if (cont.isActive) cont.resume(url)
                        }
                    }

                    try {
                        hostContainer = FrameLayout(hostActivity).apply {
                            layoutParams = FrameLayout.LayoutParams(1, 1)
                            alpha = 0f
                            isClickable = false
                        }
                        val decor = hostActivity.window.decorView as ViewGroup
                        decor.addView(hostContainer)

                        webView = WebView(hostActivity).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.userAgentString = USER_AGENT

                            addJavascriptInterface(
                                JsBridge { url ->
                                    handler.post { complete(url) }
                                },
                                "EnigmaStream"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val url = request.url.toString()
                                    pickStreamUrl(url)?.let { found ->
                                        handler.post { complete(found) }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    view?.evaluateJavascript(EXTRACT_JS, null)
                                }
                            }
                            hostContainer?.addView(
                                this,
                                FrameLayout.LayoutParams(1, 1)
                            )
                            loadUrl(embedUrl, mapOf("Referer" to refererHeader))
                        }

                        handler.postDelayed({ complete(null) }, 14_000)
                    } catch (_: Exception) {
                        complete(null)
                    }

                    cont.invokeOnCancellation {
                        handler.post {
                            if (finished.compareAndSet(false, true)) {
                                safeDestroyWebView(webView, hostContainer)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun safeDestroyWebView(webView: WebView?, container: FrameLayout?) {
        try {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            (webView?.parent as? ViewGroup)?.removeView(webView)
            container?.let { parent ->
                (parent.parent as? ViewGroup)?.removeView(parent)
            }
            webView?.destroy()
        } catch (_: Exception) {
            // Ignore teardown races
        }
    }

    private fun pickStreamUrl(url: String): String? {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return null
        if (lower.contains("thumbnail") || lower.contains("preview") || lower.contains("sprite")) return null
        if (lower.contains(".m3u8")) return cleanUrl(url)
        if (lower.contains(".mp4")) return cleanUrl(url)
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
