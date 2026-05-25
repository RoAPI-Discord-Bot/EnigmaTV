package com.enigma.tv.ui

import android.webkit.WebView

object LiveWebContent {
    /** Always load the resolved player URL in the top-level WebView (no sandbox iframe wrapper). */
    fun load(webView: WebView, embedUrl: String) {
        val trimmed = embedUrl.trim()
        if (trimmed.isBlank()) return
        webView.loadUrl(trimmed)
    }
}
