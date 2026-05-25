package com.enigma.tv.ui

import android.net.Uri
import android.webkit.WebView

object LiveWebContent {
    fun needsIframeWrapper(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.contains("/api/stream/") ||
            (u.contains("streamed.pk") && u.contains("/api/")) ||
            u.startsWith("{") ||
            u.startsWith("[")
    }

    fun load(webView: WebView, embedUrl: String) {
        if (needsIframeWrapper(embedUrl)) loadInPlayer(webView, embedUrl) else webView.loadUrl(embedUrl)
    }

    /** Load sports embed inside a fullscreen iframe so JSON/API bodies never paint as text. */
    fun loadInPlayer(webView: WebView, embedUrl: String) {
        val trimmed = embedUrl.trim()
        if (trimmed.isBlank()) return
        val base = Uri.parse(trimmed).let { uri ->
            "${uri.scheme}://${uri.host}/"
        }
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
              <style>
                html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
                iframe{position:fixed;inset:0;width:100%;height:100%;border:0;background:#000}
              </style>
            </head>
            <body>
              <iframe src="${escapeHtml(trimmed)}"
                allow="autoplay;fullscreen;encrypted-media;picture-in-picture"
                allowfullscreen referrerpolicy="no-referrer-when-downgrade"></iframe>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)
    }

    private fun escapeHtml(url: String): String =
        url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
