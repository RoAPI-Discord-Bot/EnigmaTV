package com.enigma.tv.ui

import android.net.Uri
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Blocks hijack redirects and popups in the player WebView.
 * Unlike iframe sandbox in the browser, we can intercept navigation here without
 * breaking embed hosts — we only block main-frame jumps to unrelated/ad domains.
 */
class WebViewNavigationGuard(initialUrl: String) {

    private val allowedRoots = mutableSetOf<String>()
    private var blockedCount = 0

    var onBlocked: ((String) -> Unit)? = null
    var onPageLoading: ((Boolean) -> Unit)? = null

    init {
        resetForUrl(initialUrl)
    }

    fun resetForUrl(url: String, liveTv: Boolean = false) {
        allowedRoots.clear()
        extractHost(url)?.let { registerAllowedHost(it) }
        STREAM_EMBED_ROOTS.forEach { allowedRoots.add(it) }
        if (liveTv) LIVE_TV_ROOTS.forEach { allowedRoots.add(it) }
    }

    fun shouldAllowNavigation(url: String, isMainFrame: Boolean): Boolean {
        if (!isMainFrame) return true

        val uri = parseUri(url) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false

        if (scheme !in ALLOWED_SCHEMES) return false
        if (isBlockedScheme(scheme)) return false

        val host = uri.host?.lowercase() ?: return false
        if (isBlockedHost(host)) return false
        if (isAllowedHost(host)) return true

        blockedCount++
        onBlocked?.invoke(url)
        return false
    }

    fun shouldBlockSubresource(url: String): Boolean {
        val host = parseUri(url)?.host?.lowercase() ?: return false
        return isBlockedHost(host) || AD_RESOURCE_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun configureWebView(webView: WebView, liveTv: Boolean = false) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(liveTv)
            javaScriptCanOpenWindowsAutomatically = liveTv
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                blockedCount++
                onBlocked?.invoke("popup_window")
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                onPageLoading?.invoke(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                onPageLoading?.invoke(false)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val allowed = shouldAllowNavigation(
                    request.url.toString(),
                    request.isForMainFrame
                )
                return !allowed
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !shouldAllowNavigation(url, isMainFrame = true)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (request.isForMainFrame) return null
                val url = request.url.toString()
                return if (shouldBlockSubresource(url)) emptyResponse() else null
            }
        }
    }

    private fun registerAllowedHost(host: String) {
        allowedRoots.add(host)
        registrableDomain(host)?.let { allowedRoots.add(it) }
    }

    private fun isAllowedHost(host: String): Boolean {
        if (allowedRoots.any { host == it || host.endsWith(".$it") }) return true
        return STREAMING_CDN_SUFFIXES.any { suffix ->
            host == suffix.removePrefix(".") || host.endsWith(suffix)
        }
    }

    private fun isBlockedHost(host: String): Boolean =
        BLOCKED_NAVIGATION_HOSTS.any { host == it || host.endsWith(".$it") } ||
            AD_RESOURCE_HOSTS.any { host == it || host.endsWith(".$it") }

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

    private fun isBlockedScheme(scheme: String): Boolean =
        scheme in BLOCKED_SCHEMES

    private fun emptyResponse() = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )

    companion object {
        private val ALLOWED_SCHEMES = setOf("http", "https", "about")

        private val BLOCKED_SCHEMES = setOf(
            "intent", "market", "tel", "sms", "mailto", "geo", "javascript", "file"
        )

        /** Root domains for embed providers used in StreamSources */
        private val STREAM_EMBED_ROOTS = setOf(
            "vidlink.pro",
            "vidsrc.to",
            "vidsrc.cc",
            "vidsrc.me",
            "vsembed.ru",
            "embed.su",
            "multiembed.mov",
            "2embed.skin",
            "www.2embed.skin",
            "superembed.stream",
            "multiembed.mov",
            "multiembed.xyz"
        )

        private val LIVE_TV_ROOTS = setOf(
            "youtube.com",
            "youtube-nocookie.com",
            "youtu.be",
            "googlevideo.com",
            "ytimg.com",
            "ggpht.com",
            "googleusercontent.com",
            "embedsports.top",
            "streamed.pk",
            "streamed.su",
            "dlhd.dad",
            "dlhd.click",
            "daddylivehd",
            "liveembed.net",
            "sportsonline",
            "topembed",
            "casthill.net",
            "sportcast",
            "dlhd.sx",
            "papaahd",
            "givemereddit",
            "ripplestream",
            "sportshd"
        )

        /** Common video CDN / player infrastructure (subresource + navigations) */
        private val STREAMING_CDN_SUFFIXES = listOf(
            ".cloudfront.net",
            ".akamaized.net",
            ".fastly.net",
            ".bunnycdn.com",
            ".jsdelivr.net",
            ".vercel.app",
            ".workers.dev",
            ".bitmovin.com",
            ".jwplayer.com",
            ".radiantmediatechs.com"
        )

        private val BLOCKED_NAVIGATION_HOSTS = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "googletagmanager.com",
            "facebook.com",
            "fb.com",
            "popads.net",
            "propellerads.com",
            "adsterra.com",
            "exoclick.com",
            "clickadu.com",
            "onclickads.net",
            "syndication.exoclick.com",
            "chaturbate.com",
            "stripchat.com",
            "aliexpress.com",
            "amazon.com",
            "ebay.com"
        )

        private val AD_RESOURCE_HOSTS = BLOCKED_NAVIGATION_HOSTS + setOf(
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "static.ads-twitter.com",
            "ads.yahoo.com",
            "outbrain.com",
            "taboola.com",
            "mgid.com",
            "revcontent.com"
        )
    }
}
