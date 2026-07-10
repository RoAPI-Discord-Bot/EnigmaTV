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
            // Live TV / embed proxies often require the FULL embed URL as the Referer, not just the host domain.
            var referer = embedUrl
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

        fun vidLink(streamUrl: String): ResolvedStream {
            var referer = "https://vidlink.pro/"
            var origin = "https://vidlink.pro"
            
            var cleanUrl = streamUrl
            try {
                // Use regex instead of Uri.parse since unencoded JSON in the URL can crash the parser
                val match = Regex("""([?&])headers=([^&]+)(&|)""").find(streamUrl)
                if (match != null) {
                    val rawJson = match.groupValues[2]
                    val decoded = java.net.URLDecoder.decode(rawJson, "UTF-8")
                    val jsonObj = org.json.JSONObject(decoded)
                    if (jsonObj.has("referer")) referer = jsonObj.getString("referer")
                    if (jsonObj.has("origin")) origin = jsonObj.getString("origin")
                    
                    val prefix = match.groupValues[1]
                    val suffix = match.groupValues[3]
                    val replacement = if (prefix == "?" && suffix == "&") "?" else ""
                    cleanUrl = streamUrl.replace(match.value, replacement)
                }
            } catch (e: Exception) {
                android.util.Log.e("ResolvedStream", "Failed to parse headers from $streamUrl", e)
            }

            return ResolvedStream(
                url = cleanUrl,
                referer = referer,
                origin = origin,
                provider = "VidLink"
            )
        }

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
