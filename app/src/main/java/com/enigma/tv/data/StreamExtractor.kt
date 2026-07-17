package com.enigma.tv.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        Log.d(TAG, "extractStreamUrl: embed=$embedUrl referer=$referer")
        val result = extractStreamUrlAndSubtitle(embedUrl, referer, activity) ?: run {
            Log.w(TAG, "extractStreamUrl: extractor returned null for $embedUrl")
            return null
        }
        val streamUrl = result.streamUrl
        val subtitleUrl = result.subtitleUrl
        val thumbnailUrl = result.thumbnailUrl
        Log.i(TAG, "extractStreamUrl: RESOLVED url=$streamUrl subtitle=$subtitleUrl thumb=$thumbnailUrl")
        // Use fromEmbed so the headers={} query param on the stream URL is parsed:
        // e.g. https://cdn.example.com/video.mp4?headers={"referer":"https://filmboom.top/","origin":"https://filmboom.top"}
        // fromEmbed() reads those and uses them as the actual Referer/Origin HTTP headers ExoPlayer sends.
        return ResolvedStream.fromEmbed(
            embedUrl = referer ?: embedUrl,
            streamUrl = streamUrl,
            provider = "webview",
            cookies = result.cookies
        ).copy(subtitleUrl = subtitleUrl, thumbnailUrl = thumbnailUrl)
    }

    data class ExtractionResult(val streamUrl: String, val subtitleUrl: String?, val thumbnailUrl: String?, val cookies: String = "")

    /** Returns ExtractionResult */
    suspend fun extractStreamUrlAndSubtitle(
        embedUrl: String,
        referer: String? = null,
        activity: Activity? = null
    ): ExtractionResult? {
        val ctx = activity ?: context
        return withTimeoutOrNull(18_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val finished = AtomicBoolean(false)
                    val capturedSubtitle = AtomicReference<String?>(null)
                    val capturedThumbnail = AtomicReference<String?>(null)
                    val capturedStream = AtomicReference<String?>(null)
                    var capturedStreamScore = -1  // Track quality score of best stream so far
                    val refererHeader = referer ?: embedUrl
                    val handler = Handler(Looper.getMainLooper())
                    var webView: WebView? = null
                    var hostContainer: FrameLayout? = null

                    fun complete(url: String?) {
                        handler.post {
                            if (!finished.compareAndSet(false, true)) return@post
                            handler.removeCallbacksAndMessages(null)
                            // Capture cookies from the WebView session BEFORE destroying it.
                            // AutoEmbed CDN URLs are session-signed — ExoPlayer needs these
                            // cookies to fetch segments without getting 403.
                            val cookieStr = try {
                                val cm = android.webkit.CookieManager.getInstance()
                                val streamUrl = capturedStream.get()
                                if (url != null && streamUrl != null) {
                                    // Get cookies for both the CDN and embed domains
                                    val cdnCookies = cm.getCookie(url) ?: ""
                                    val embedCookies = cm.getCookie(embedUrl) ?: ""
                                    listOf(cdnCookies, embedCookies)
                                        .filter { it.isNotBlank() }
                                        .joinToString("; ")
                                } else ""
                            } catch (_: Exception) { "" }
                            safeDestroyWebView(webView, hostContainer)
                            webView = null
                            hostContainer = null
                            if (cont.isActive) cont.resume(
                                if (url != null) ExtractionResult(url, capturedSubtitle.get(), capturedThumbnail.get(), cookieStr) else null
                            )
                        }
                    }

                    fun injectHooks(view: WebView?) {
                        view?.evaluateJavascript(HOOK_JS, null)
                    }

                    try {
                        if (activity != null) {
                            hostContainer = FrameLayout(activity).apply {
                                layoutParams = FrameLayout.LayoutParams(1, 1)
                                alpha = 0f
                                isClickable = false
                            }
                            val decor = activity.window.decorView as ViewGroup
                            decor.addView(hostContainer)
                        }

                        webView = WebView(ctx).apply {
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
                                    onStream = { url ->
                                        // Route JS-intercepted URLs through the same score-based
                                        // upgrade system as shouldInterceptRequest. This prevents
                                        // a 360p/index.m3u8 (the FIRST fetch) from locking quality.
                                        pickStreamUrl(url)?.let { found ->
                                            val score = masterScore(found)
                                            Log.d(TAG, "JsBridge stream: score=$score url=$found")
                                            synchronized(capturedStream) {
                                                val existing = capturedStream.get()
                                                if (existing == null || score > capturedStreamScore) {
                                                    Log.i(TAG, "JsBridge UPGRADE: score $capturedStreamScore->$score url=$found")
                                                    capturedStream.set(found)
                                                    capturedStreamScore = score
                                                    handler.removeCallbacksAndMessages(null)
                                                    // Smart delay: fire faster the higher-quality the stream.
                                                    // Master/true adaptive: almost immediate (200ms for subtitle)
                                                    // Any m3u8 (including quality-specific HLS): 600ms
                                                    // MP4 fallback: 1500ms (give HLS a chance to appear)
                                                    val delayMs = when {
                                                        score >= 100 -> 200L
                                                        score >= 50  -> 600L
                                                        else         -> 1500L
                                                    }
                                                    handler.postDelayed({ complete(capturedStream.get()) }, delayMs)
                                                } else {
                                                    Log.d(TAG, "JsBridge SKIP (lower score $score <= $capturedStreamScore): $found")
                                                }
                                            }
                                        }
                                    },
                                    onSubtitle = { url -> 
                                        if (url.contains("thumbnail", ignoreCase = true) || url.contains("sprite", ignoreCase = true)) {
                                            capturedThumbnail.compareAndSet(null, url)
                                        } else {
                                            capturedSubtitle.compareAndSet(null, url) 
                                        }
                                    }
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
                                        val score = masterScore(found)
                                        Log.d(TAG, "Intercept stream: score=$score url=$found")
                                        synchronized(capturedStream) {
                                            val existing = capturedStream.get()
                                            if (existing == null || score > capturedStreamScore) {
                                                Log.i(TAG, "Intercept UPGRADE: score $capturedStreamScore->$score url=$found")
                                                capturedStream.set(found)
                                                capturedStreamScore = score
                                                handler.removeCallbacksAndMessages(null)
                                                // Same smart delay as JsBridge path
                                                val delayMs = when {
                                                    score >= 100 -> 200L
                                                    score >= 50  -> 600L
                                                    else         -> 1500L
                                                }
                                                handler.postDelayed({ complete(capturedStream.get()) }, delayMs)
                                            } else {
                                                Log.d(TAG, "Intercept SKIP (lower score $score <= $capturedStreamScore): $found")
                                            }
                                        }
                                    }
                                    pickSubtitleUrl(reqUrl)?.let { sub ->
                                        if (capturedSubtitle.compareAndSet(null, sub)) {
                                            val stream = capturedStream.get()
                                            if (stream != null) handler.post { complete(stream) }
                                        }
                                    }
                                    pickThumbnailUrl(reqUrl)?.let { thumb ->
                                        capturedThumbnail.compareAndSet(null, thumb)
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    injectHooks(view)
                                }

                                override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                    handler?.proceed()
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
    ): String? = extractStreamUrlAndSubtitle(embedUrl, referer, activity)?.streamUrl

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
        // Skip TS segments — these are individual video chunks, not playlists
        if (lower.contains(".ts?") || lower.endsWith(".ts")) return null
        if (lower.contains(".m3u8")) return cleanUrl(url)
        if (lower.contains(".mp4") && !lower.contains("poster")) return cleanUrl(url)
        if (lower.contains("/master.") || lower.contains("playlist.m3u8")) return cleanUrl(url)
        return null
    }

    /**
     * Scores a stream URL — higher = better quality / more likely to be a master playlist.
     * Master playlists give ExoPlayer full adaptive bitrate selection ability.
     */
    private fun masterScore(url: String): Int {
        val lower = url.lowercase()
        // Explicit master playlist indicators only — index.m3u8 is NOT a master,
        // it's a quality-specific segment playlist (e.g. 360p/index.m3u8)
        
        // True adaptive masters
        if (lower.contains("master.m3u8") || lower.contains("playlist.m3u8")) return 100
        
        // Specific resolutions
        if (lower.contains("4k") || lower.contains("2160p") || lower.contains("/2160/")) return 95
        if (lower.contains("1080p") || lower.contains("/1080/")) return 90
        if (lower.contains("720p") || lower.contains("/720/") || lower.contains("/hd/")) return 80
        if (lower.contains("480p") || lower.contains("/480/") || lower.contains("/sd/")) return 70
        if (lower.contains("360p") || lower.contains("/360/")) return 60
        
        // M3U8 without resolution (might be master with weird name)
        if (lower.contains(".m3u8")) return 50
        
        // MP4 fallback (often 360p)
        if (lower.contains(".mp4")) return 10
        
        return 0
    }

    private fun pickSubtitleUrl(url: String): String? {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return null
        if (!lower.contains(".vtt") && !lower.contains(".srt")) return null
        
        // Prevent thumbnail/sprite VTT files from being parsed as captions
        if (lower.contains("thumbnail") || lower.contains("sprite") || lower.contains("preview")) return null
        // Prefer English subtitle files
        if (lower.contains("eng") || lower.contains("en-") || lower.contains("/en/") ||
            lower.contains("english") || lower.contains("-2.vtt") || lower.contains("_en.")) {
            return cleanUrl(url)
        }
        // Accept any subtitle as fallback
        return cleanUrl(url)
    }

    private fun pickThumbnailUrl(url: String): String? {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return null
        if (!lower.contains(".vtt")) return null
        if (lower.contains("thumbnail") || lower.contains("sprite") || lower.contains("preview")) {
            return cleanUrl(url)
        }
        return null
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
        private const val TAG = "StreamExtractor"
        const val USER_AGENT = StreamResolver.USER_AGENT

        // Stream quality scores — higher wins. Kept for reference / future use.
        // masterScore() returns these values. SCORE_MASTER threshold is 100 (true adaptive masters).
        // Any m3u8 (score >= 50) fires in 600ms; MP4 (score=10) fires in 1500ms; master fires in 200ms.

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
      if (el.tagName === 'VIDEO') { el.muted = true; el.volume = 0; }
      var s = el.src || el.getAttribute('src');
      if (s && (s.indexOf('.m3u8') >= 0 || s.indexOf('.mp4') >= 0)) EnigmaStream.onStreamFound(s);
    });
  } catch(e) {}
})();
"""
    }
}
