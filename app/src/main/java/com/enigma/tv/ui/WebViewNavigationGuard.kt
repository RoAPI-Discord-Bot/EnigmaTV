package com.enigma.tv.ui

import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Embed player guard: **blocklist-only** for main navigations so play-button redirects
 * to file hosts (megacloud, rabbitstream, etc.) are not cancelled.
 * Popups are blocked; [EmbedPlayerShield] removes in-page click-hijack overlays on pause.
 */
class WebViewNavigationGuard(initialUrl: String) {

    private val sessionHosts = mutableSetOf<String>()
    private var liveTvMode = false
    private var suppressLoadingPulses = false
    private var streamPlaying = false
    private var resumeTargetMs: Long = 0L
    private var didSeekResume = false

    var onBlocked: ((String) -> Unit)? = null
    var onPageLoading: ((Boolean) -> Unit)? = null
    var onStreamUrl: ((String) -> Unit)? = null
    var onPlaybackProbe: ((Boolean) -> Unit)? = null
    var onLiveWaiting: (() -> Unit)? = null
    var onPlaybackProgress: ((Long) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onStreamFailed: (() -> Unit)? = null

    init {
        resetForUrl(initialUrl)
    }

    fun isStreamPlaying(): Boolean = streamPlaying

    fun resetForUrl(url: String, liveTv: Boolean = false, resumePositionMs: Long = 0L) {
        liveTvMode = liveTv
        suppressLoadingPulses = false
        streamPlaying = false
        resumeTargetMs = resumePositionMs.coerceAtLeast(0L)
        didSeekResume = false
        sessionHosts.clear()
        extractHost(url)?.let { registerHost(it) }
        STREAM_EMBED_ROOTS.forEach { registerHost(it) }
        if (liveTv) LIVE_TV_ROOTS.forEach { registerHost(it) }
    }

    /**
     * @return true if this main-frame navigation should be **cancelled**
     */
    fun shouldBlockNavigation(url: String, isMainFrame: Boolean, userGesture: Boolean): Boolean {
        if (!isMainFrame) return false

        val uri = parseUri(url) ?: return true
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme !in ALLOWED_SCHEMES) return true
        if (isBlockedScheme(scheme)) return true

        val host = uri.host?.lowercase() ?: return true
        if (liveTvMode && isMainFrame && looksLikeStreamApi(url)) {
            onBlocked?.invoke(url)
            return true
        }
        if (isBlockedHost(host)) {
            onBlocked?.invoke(url)
            return true
        }

        // User tapped play — allow redirect chains to player/file hosts
        if (userGesture || looksLikePlayerNavigation(url)) {
            registerHost(host)
            return false
        }

        // Programmatic hops from embed → player: allow if streaming-related
        if (looksLikePlayerNavigation(url) || isStreamingCdn(host)) {
            registerHost(host)
            return false
        }

        // Obvious ad / store hijacks without user intent
        if (isObviousHijack(url, host)) {
            onBlocked?.invoke(url)
            return true
        }

        registerHost(host)
        return false
    }

    fun shouldBlockSubresource(url: String): Boolean {
        val host = parseUri(url)?.host?.lowercase() ?: return false
        return isBlockedHost(host)
    }

    fun configureWebView(webView: WebView, liveTv: Boolean = false) {
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 85) {
                    view?.let {
                        EmbedPlayerShield.apply(it)
                        trySeekResume(it)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let { extractHost(it)?.let { registerHost(it) } }
                // Inject shield immediately at page start so our document.createElement override
                // intercepts iframe sandbox attributes BEFORE the page's own scripts run.
                view?.let { EmbedPlayerShield.apply(it) }
                if (!(liveTvMode && suppressLoadingPulses)) {
                    onPageLoading?.invoke(true)
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                if (liveTvMode) view?.let { web ->
                    web.postDelayed({ probePlayback(web) }, 1200)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { extractHost(it)?.let { registerHost(it) } }
                view?.let { web ->
                    EmbedPlayerShield.apply(web)
                    EmbedPlayerShield.startPeriodic(web)
                    web.postDelayed({ probePlayback(web) }, 2200)
                    if (!liveTvMode && resumeTargetMs >= 3_000L) {
                        scheduleResumeAttempts(web)
                    } else {
                        web.postDelayed({ trySeekResume(web) }, 3500)
                    }
                    web.postDelayed({ probePlaybackProgress(web) }, 8000)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val block = shouldBlockNavigation(
                    request.url.toString(),
                    request.isForMainFrame,
                    userGesture = request.isRedirect || request.hasGesture()
                )
                return request.isForMainFrame && block
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return shouldBlockNavigation(url, isMainFrame = true, userGesture = true)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (request.isForMainFrame) return null
                val url = request.url.toString()
                captureStreamUrl(url)
                return if (shouldBlockSubresource(url)) emptyResponse() else null
            }
        }
    }

    private fun registerHost(host: String) {
        sessionHosts.add(host)
        registrableDomain(host)?.let { sessionHosts.add(it) }
    }

    private fun isStreamingCdn(host: String): Boolean =
        STREAMING_CDN_SUFFIXES.any { suffix ->
            host == suffix.removePrefix(".") || host.endsWith(suffix)
        }

    private fun looksLikePlayerNavigation(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mp4")) return true
        return PLAYER_PATH_HINTS.any { lower.contains(it) }
    }

    private fun isObviousHijack(url: String, host: String): Boolean {
        if (isBlockedHost(host)) return true
        val lower = url.lowercase()
        return HIJACK_PATH_HINTS.any { lower.contains(it) }
    }

    private fun isBlockedHost(host: String): Boolean =
        BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }

    private fun extractHost(url: String): String? = parseUri(url)?.host?.lowercase()

    private fun registrableDomain(host: String): String? {
        val parts = host.split('.')
        if (parts.size < 2) return host
        val twoPartTlds = setOf("co.uk", "com.au", "co.nz", "com.br")
        val lastTwo = parts.takeLast(2).joinToString(".")
        val lastThree = parts.takeLast(3).joinToString(".")
        return if (twoPartTlds.any { host.endsWith(it) && parts.size >= 3 }) lastThree else lastTwo
    }

    private fun parseUri(url: String): Uri? = try {
        Uri.parse(url)
    } catch (_: Exception) {
        null
    }

    private fun isBlockedScheme(scheme: String): Boolean = scheme in BLOCKED_SCHEMES

    private fun captureStreamUrl(url: String) {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return
        if (lower.contains("thumbnail") || lower.contains("preview") || lower.contains("sprite")) return
        if (lower.contains(".m3u8") || (lower.contains(".mp4") && !lower.contains("poster"))) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onStreamUrl?.invoke(url)
            }
        }
    }

    private fun scheduleResumeAttempts(webView: WebView) {
        listOf(800L, 2000L, 3500L, 5500L, 8000L, 12_000L, 18_000L, 26_000L).forEach { delayMs ->
            webView.postDelayed({ trySeekResume(webView) }, delayMs)
        }
    }

    private fun trySeekResume(webView: WebView) {
        if (didSeekResume || resumeTargetMs < 3_000L) return
        val sec = resumeTargetMs / 1000.0
        webView.evaluateJavascript(
            """
            (function(){
              try {
                var v = document.querySelector('video');
                if (!v) return 'wait';
                if (v.readyState < 1) return 'wait';
                if (Math.abs(v.currentTime - $sec) > 2) v.currentTime = $sec;
                return 'ok';
              } catch(e) { return 'wait'; }
            })();
            """.trimIndent()
        ) { raw ->
            if (raw?.contains("ok") == true) didSeekResume = true
        }
    }

    private fun probePlaybackProgress(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function(){
              try {
                var v = document.querySelector('video');
                if (!v) return '0';
                if (v.duration && v.duration >= 30 && v.currentTime >= v.duration - 8) return 'ended';
                return String(Math.max(0, Math.floor(v.currentTime * 1000)));
              } catch(e) { return '0'; }
            })();
            """.trimIndent()
        ) { raw ->
            val v = raw?.trim('"', ' ', '\'') ?: "0"
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                when (v) {
                    "ended" -> onPlaybackEnded?.invoke()
                    else -> v.toLongOrNull()?.takeIf { it > 0L }?.let { onPlaybackProgress?.invoke(it) }
                }
            }
        }
    }

    private fun probePlayback(webView: WebView) {
        val liveJs = if (liveTvMode) "true" else "false"
        webView.evaluateJavascript(
            """
            (function(){
              try {
                var live = $liveJs;
                var b = document.body ? document.body.innerText.trim().toLowerCase() : '';
                if (b.includes("we couldn't find this content") || b.includes("we couldn\\\'t find this content") || b.includes("we don't have this") || b.includes("we dont have this") || b.includes("no media found") || b.includes("media not found") || b.includes("404 not found") || (b === "not found") || b.includes("server error")) return 'notfound';
                if (b.length > 0 && b.length < 12000 && (b.charAt(0)==='{' || b.charAt(0)==='[')) return 'json';
                var v = document.querySelector('video');
                if (v) {
                  if (v.readyState >= 2 && (v.videoWidth > 0 || v.duration > 0)) return 'ok';
                  return live ? 'waiting' : 'ok';
                }
                if (live) {
                  if (document.querySelector('iframe')) return 'ok';
                  return 'empty';
                }
                if (document.querySelector('iframe')) return 'ok';
                if (/player|plyr|jwplayer|video-js/i.test(document.documentElement.innerHTML)) return 'ok';
                return 'empty';
              } catch(e) { return 'empty'; }
            })();
            """.trimIndent()
        ) { raw ->
            val verdict = raw?.trim('"', ' ') ?: "empty"
            val isJsonWall = verdict == "json"
            val notFound = verdict == "notfound"
            val ok = verdict == "ok"
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (notFound) {
                    onStreamFailed?.invoke()
                    return@post
                }
                if (liveTvMode && (ok || isJsonWall)) {
                    webView.post { hideRawTextOverlay(webView) }
                }
                if (ok) {
                    suppressLoadingPulses = true
                    streamPlaying = true
                }
                if (liveTvMode && isJsonWall) {
                    onPlaybackProbe?.invoke(false)
                    onPageLoading?.invoke(false)
                    onLiveWaiting?.invoke()
                    return@post
                }
                onPlaybackProbe?.invoke(ok)
                onPageLoading?.invoke(!ok)
                if (liveTvMode && !ok && !streamPlaying) onLiveWaiting?.invoke()
            }
        }
    }

    private fun hideRawTextOverlay(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function(){
              try {
                document.documentElement.style.background='#000';
                document.body.style.background='#000';
                document.body.style.color='transparent';
                document.querySelectorAll('pre,code').forEach(function(el){
                  el.style.display='none';
                });
                document.querySelectorAll('body *').forEach(function(el){
                  var t = (el.innerText || '').toLowerCase();
                  if (t.indexOf('sandbox') >= 0 && t.length < 240) el.style.display='none';
                });
              } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    private fun looksLikeStreamApi(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("/api/stream/") || (u.contains("streamed.pk") && u.contains("/api/"))
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )

    companion object {
        private val ALLOWED_SCHEMES = setOf("http", "https", "about", "blob")

        private val BLOCKED_SCHEMES = setOf(
            "intent", "market", "tel", "sms", "mailto", "geo", "javascript", "file"
        )

        private val PLAYER_PATH_HINTS = listOf(
            "/embed/", "/e/", "/play", "/player", "/watch", "/stream",
            "/movie", "/tv/", "/video", "/file/", "/hls", "/source",
            "tmdb=", "video_id=", "autoplay"
        )

        private val HIJACK_PATH_HINTS = listOf(
            "/redirect?", "clickid=", "affiliate", "/out?", "/go?",
            "doubleclick", "utm_campaign=ad"
        )

        private val STREAM_EMBED_ROOTS = setOf(
            "vidlink.pro",
            "vidsrc.to", "vidsrc.cc", "vidsrc.me", "vidsrc.net", "vidsrc.xyz", "vidsrc.fyi",
            "cineby.gd", "vsembed.ru", "embed.su", "multiembed.mov", "2embed.skin",
            "www.2embed.skin", "superembed.stream", "multiembed.xyz"
        )

        private val LIVE_TV_ROOTS = setOf(
            "youtube.com", "youtube-nocookie.com", "youtu.be", "googlevideo.com",
            "ytimg.com", "ggpht.com", "googleusercontent.com", "embedsports.top",
            "streamed.pk", "streamed.su", "dlhd.dad", "dlhd.click", "liveembed.net",
            "topembed", "casthill.net", "sportcast", "dlhd.sx", "givemereddit",
            "ripplestream", "sportshd"
        )

        private val STREAMING_CDN_SUFFIXES = listOf(
            ".cloudfront.net", ".akamaized.net", ".fastly.net", ".bunnycdn.net",
            ".bunnycdn.com", ".workers.dev", ".vercel.app", ".bitmovin.com",
            ".jwplayer.com", ".videodelivery.net", ".streamtape.com", ".mixdrop",
            ".uqload", ".dood", ".voe.sx", ".filemoon", ".rabbitstream", ".megacloud",
            ".warezcdn", ".neonnetwork", ".shadowlands", ".cdn77", ".gvideo",
            ".mcloud", ".embedflix", ".moviesapi", ".suicide", ".proxy", ".netlify"
        )

        private val BLOCKED_HOSTS = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "facebook.com", "fb.com",
            "popads.net", "propellerads.com", "adsterra.com", "exoclick.com",
            "clickadu.com", "onclickads.net", "chaturbate.com", "stripchat.com",
            "aliexpress.com", "amazon.com", "ebay.com", "adservice.google.com",
            "pagead2.googlesyndication.com", "outbrain.com", "taboola.com", "mgid.com",
            "revcontent.com", "ads.yahoo.com", "static.ads-twitter.com"
        )

    }
}
