package com.enigma.tv.data

import android.net.Uri

/**
 * Direct playable stream plus headers CDNs expect (Referer/Origin) — required for VidLink/Vidsrc HLS.
 */
data class ResolvedStream(
    val url: String,
    val referer: String = "",
    val origin: String = "",
    val userAgent: String = StreamResolver.USER_AGENT,
    val provider: String = "direct",
    val subtitleUrl: String? = null,
    val cookies: String = ""
) {
    fun playbackHeaders(): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        if (referer.isNotBlank()) headers["Referer"] = referer
        if (origin.isNotBlank()) headers["Origin"] = origin
        if (cookies.isNotBlank()) headers["Cookie"] = cookies
        return headers
    }

    companion object {
        fun fromEmbed(embedUrl: String, streamUrl: String, provider: String, cookies: String = "", userAgent: String = StreamResolver.USER_AGENT): ResolvedStream {
            var referer = embedReferer(embedUrl)
            var origin = embedOrigin(embedUrl)

            try {
                val uri = Uri.parse(streamUrl)
                val headersJson = uri.getQueryParameter("headers")
                if (headersJson != null) {
                    val jsonObj = org.json.JSONObject(headersJson)
                    if (jsonObj.has("referer")) referer = jsonObj.getString("referer")
                    if (jsonObj.has("origin")) origin = jsonObj.getString("origin")
                }
            } catch (_: Exception) {}

            return ResolvedStream(
                url = streamUrl,
                referer = referer,
                origin = origin,
                provider = provider,
                cookies = cookies,
                userAgent = userAgent
            )
        }

        fun vidLink(streamUrl: String): ResolvedStream = ResolvedStream(
            url = streamUrl,
            referer = "https://vidlink.pro/",
            origin = "https://vidlink.pro",
            provider = "VidLink"
        )

        fun embedReferer(embedUrl: String): String = try {
            val uri = Uri.parse(embedUrl)
            val host = uri.host ?: return embedUrl
            "${uri.scheme}://$host/"
        } catch (_: Exception) {
            embedUrl
        }

        fun embedOrigin(embedUrl: String): String = try {
            val uri = Uri.parse(embedUrl)
            val host = uri.host ?: return ""
            "${uri.scheme}://$host"
        } catch (_: Exception) {
            ""
        }
    }
}
