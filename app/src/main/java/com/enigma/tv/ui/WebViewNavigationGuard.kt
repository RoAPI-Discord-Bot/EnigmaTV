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

    var onBlocked: ((String) -> Unit)? = null
    var onPageLoading: ((Boolean) -> Unit)? = null
    var onStreamUrl: ((String) -> Unit)? = null

    init {
        resetForUrl(initialUrl)
    }

    fun resetForUrl(url: String, liveTv: Boolean = false) {
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
        false
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
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean = false

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 85) view?.let { EmbedPlayerShield.apply(it) }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let { extractHost(it)?.let { registerHost(it) } }
                onPageLoading?.invoke(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { extractHost(it)?.let { registerHost(it) } }
                view?.let {
                    EmbedPlayerShield.apply(it)
                    EmbedPlayerShield.startPeriodic(it)
                }
                onPageLoading?.invoke(false)
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
