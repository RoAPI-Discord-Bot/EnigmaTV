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

    var onBlocked: ((String) -> Unit)? = null
    var onPageLoading: ((Boolean) -> Unit)? = null
    var onStreamUrl: ((String) -> Unit)? = null
    var onPlaybackProbe: ((Boolean) -> Unit)? = null
    var onPlaybackProgress: ((Int) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null

    init {
        resetForUrl(initialUrl)
    }

    fun resetForUrl(url: String, liveTv: Boolean = false) {
        liveTvMode = liveTv
        suppressLoadingPulses = false
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
                if (newProgress >= 85) view?.let { EmbedPlayerShield.apply(it) }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let { extractHost(it)?.let { registerHost(it) } }
                if (!(liveTvMode && suppressLoadingPulses)) {
                    onPageLoading?.invoke(true)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { extractHost(it)?.let { registerHost(it) } }
                view?.let { web ->
                    if (liveTvMode) hideRawTextOverlay(web)
                    EmbedPlayerShield.apply(web)
                    EmbedPlayerShield.startPeriodic(web)
                    web.postDelayed({ probePlayback(web) }, 2200)
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
                val url = request.url.toString()
                if (request.isForMainFrame) {
                    val mime = request.requestHeaders["Content-Type"]
                        ?: request.requestHeaders["content-type"]
                    if (mime?.contains("json", ignoreCase = true) == true ||
                        looksLikeStreamApi(url)
                    ) {
                        return emptyResponse()
                    }
                    return null
                }
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

    private fun probePlaybackProgress(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function(){
              try {
                var v = document.querySelector('video');
                if (!v || !v.duration || v.duration < 30) return '0';
                var pct = Math.round((v.currentTime / v.duration) * 100);
                if (pct >= 92 && v.currentTime > 10) return 'ended';
                return String(Math.min(100, Math.max(0, pct)));
              } catch(e) { return '0'; }
            })();
            """.trimIndent()
        ) { raw ->
            val v = raw?.trim('"', ' ', '\'') ?: "0"
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                when (v) {
                    "ended" -> onPlaybackEnded?.invoke()
                    else -> v.toIntOrNull()?.takeIf { it > 0 }?.let { onPlaybackProgress?.invoke(it) }
                }
            }
        }
    }

    private fun probePlayback(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function(){
              try {
                var b = document.body ? document.body.innerText.trim() : '';
                if (b.length > 0 && b.length < 12000 && (b.charAt(0)==='{' || b.charAt(0)==='[')) return 'json';
                if (document.querySelector('video')) return 'ok';
                if (document.querySelector('iframe')) return 'ok';
                if (/player|plyr|jwplayer|video-js/i.test(document.documentElement.innerHTML)) return 'ok';
                return 'empty';
              } catch(e) { return 'empty'; }
            })();
            """.trimIndent()
        ) { raw ->
            val verdict = raw?.trim('"', ' ') ?: "empty"
            val ok = verdict == "ok" ||
                (liveTvMode && (verdict == "empty" || verdict == "json"))
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (ok) suppressLoadingPulses = true
                onPlaybackProbe?.invoke(ok)
                onPageLoading?.invoke(!ok)
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
