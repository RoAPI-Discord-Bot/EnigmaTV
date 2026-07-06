package com.enigma.tv.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Hidden WebView attached to the activity window; hooks fetch/XHR and intercepts .m3u8/.mp4 traffic.
 */
class StreamExtractor(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractStreamUrl(
        embedUrl: String,
        referer: String? = null,
        activity: Activity? = null
    ): ResolvedStream? {
        val result = extractStreamUrlAndSubtitle(embedUrl, referer, activity) ?: return null
        val ref = referer ?: ResolvedStream.embedReferer(embedUrl)
        return ResolvedStream(
            url = result.first,
            referer = ref,
            origin = ResolvedStream.embedOrigin(embedUrl),
            provider = "webview",
            subtitleUrl = result.second
        )
    }

    /** Returns Pair(streamUrl, subtitleUrl?) */
    suspend fun extractStreamUrlAndSubtitle(
        embedUrl: String,
        referer: String? = null,
        activity: Activity? = null
    ): Pair<String, String?>? {
        val hostActivity = activity ?: return null
        return withTimeoutOrNull(18_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val finished = AtomicBoolean(false)
                    val capturedSubtitle = AtomicReference<String?>(null)
                    val capturedStream = AtomicReference<String?>(null)
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
                            if (cont.isActive) cont.resume(
                                if (url != null) url to capturedSubtitle.get() else null
                            )
                        }
                    }

                    fun injectHooks(view: WebView?) {
                        view?.evaluateJavascript(HOOK_JS, null)
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                settings.mixedContentMode =
                                    android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            addJavascriptInterface(
                                JsBridge(
                                    onStream = { url -> complete(url) },
                                    onSubtitle = { url -> capturedSubtitle.compareAndSet(null, url) }
                                ),
                                "EnigmaStream"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): android.webkit.WebResourceResponse? {
                                    val reqUrl = request.url.toString()
                                    pickStreamUrl(reqUrl)?.let { found ->
                                        if (capturedStream.compareAndSet(null, found)) {
                                            if (capturedSubtitle.get() != null) {
                                                handler.post { complete(found) }
                                            } else {
                                                handler.postDelayed({ complete(capturedStream.get()) }, 1800)
                                            }
                                        }
                                    }
                                    pickSubtitleUrl(reqUrl)?.let { sub ->
                                        if (capturedSubtitle.compareAndSet(null, sub)) {
                                            val stream = capturedStream.get()
                                            if (stream != null) handler.post { complete(stream) }
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    injectHooks(view)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    injectHooks(view)
                                    listOf(800L, 2000L, 4000L, 7000L).forEach { delay ->
                                        handler.postDelayed({ injectHooks(view) }, delay)
                                    }
                                    handler.postDelayed({ view?.evaluateJavascript(EXTRACT_JS, null) }, 1200)
                                }
                            }
                            hostContainer?.addView(this, FrameLayout.LayoutParams(1, 1))
                            loadUrl(embedUrl, mapOf("Referer" to refererHeader))
                        }

                        handler.postDelayed({ complete(null) }, 12_000)
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

    suspend fun extractStreamUrlRaw(
        embedUrl: String,
        referer: String? = null,
        activity: Activity? = null
    ): String? = extractStreamUrlAndSubtitle(embedUrl, referer, activity)?.first

    private fun safeDestroyWebView(webView: WebView?, container: FrameLayout?) {
        try {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            (webView?.parent as? ViewGroup)?.removeView(webView)
            container?.let { (it.parent as? ViewGroup)?.removeView(it) }
            webView?.destroy()
        } catch (_: Exception) {
        }
    }

    private fun pickStreamUrl(url: String): String? {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return null
        if (lower.contains("thumbnail") || lower.contains("preview") || lower.contains("sprite")) return null
        if (lower.contains(".m3u8")) return cleanUrl(url)
        if (lower.contains(".mp4") && !lower.contains("poster")) return cleanUrl(url)
        if (lower.contains("/master.") || lower.contains("playlist.m3u8")) return cleanUrl(url)
        return null
    }

    private fun pickSubtitleUrl(url: String): String? {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return null
        if (!lower.contains(".vtt") && !lower.contains(".srt")) return null
        // Prefer English subtitle files
        if (lower.contains("eng") || lower.contains("en-") || lower.contains("/en/") ||
            lower.contains("english") || lower.contains("-2.vtt") || lower.contains("_en.")) {
            return cleanUrl(url)
        }
        // Accept any subtitle as fallback
        return cleanUrl(url)
    }

    private fun cleanUrl(url: String): String =
        url.replace("\\/", "/").replace("\\u0026", "&").trim()

    private class JsBridge(
        private val onStream: (String) -> Unit,
        private val onSubtitle: (String) -> Unit = {}
    ) {
        @JavascriptInterface
        fun onStreamFound(url: String) {
            if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
                onStream(url)
            }
        }

        @JavascriptInterface
        fun onSubtitleFound(url: String) {
            if (url.contains(".vtt", ignoreCase = true) || url.contains(".srt", ignoreCase = true)) {
                onSubtitle(url)
            }
        }
    }

    companion object {
        const val USER_AGENT = StreamResolver.USER_AGENT

        private const val HOOK_JS = """
(function() {
  if (window.__enigmaHooked) return;
  window.__enigmaHooked = true;
  function notifyStream(u) {
    if (!u) return;
    var s = String(u);
    if (s.indexOf('.m3u8') >= 0 || s.indexOf('.mp4') >= 0) EnigmaStream.onStreamFound(s);
  }
  function notifySub(u) {
    if (!u) return;
    var s = String(u);
    if (s.indexOf('.vtt') >= 0 || s.indexOf('.srt') >= 0) EnigmaStream.onSubtitleFound(s);
  }
  var of = window.fetch;
  if (of) {
    window.fetch = function(input, init) {
      try {
        var url = typeof input === 'string' ? input : (input && input.url ? input.url : null);
        if (url) { notifyStream(url); notifySub(url); }
      } catch(e) {}
      return of.apply(this, arguments);
    };
  }
  var ox = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(method, url) {
    try { notifyStream(url); notifySub(url); } catch(e) {}
    return ox.apply(this, arguments);
  };
})();
"""

        private const val EXTRACT_JS = """
(function() {
  try {
    document.querySelectorAll('video, source, iframe').forEach(function(el) {
      var s = el.src || el.getAttribute('src');
      if (s && (s.indexOf('.m3u8') >= 0 || s.indexOf('.mp4') >= 0)) EnigmaStream.onStreamFound(s);
    });
  } catch(e) {}
})();
"""
    }
}
