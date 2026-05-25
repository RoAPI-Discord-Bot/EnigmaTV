package com.enigma.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Unwraps live sports embed pages so WebView loads the real player URL
 * instead of a wrapper that shows sandbox iframe errors.
 */
object LiveEmbedResolver {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val iframeSrcRegex = Regex(
        """<iframe[^>]+src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    suspend fun resolvePlayableUrl(embedUrl: String): String = withContext(Dispatchers.IO) {
        StreamResolver.resolveDirectUrl(embedUrl)?.let { return@withContext it }
        unwrapIframe(embedUrl) ?: embedUrl
    }

    private fun unwrapIframe(pageUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", pageUrl)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string() ?: return@use null
                val candidates = iframeSrcRegex.findAll(html)
                    .map { it.groupValues[1] }
                    .map { decode(it) }
                    .filter { it.startsWith("http") }
                    .distinct()
                    .toList()
                candidates.firstOrNull { looksLikePlayer(it) } ?: candidates.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikePlayer(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("embed") || u.contains("player") || u.contains("stream") ||
            u.contains(".m3u8") || u.contains("topembed") || u.contains("liveembed") ||
            u.contains("dlhd") || u.contains("sportsonline")
    }

    private fun decode(raw: String): String =
        raw.replace("\\/", "/").replace("&amp;", "&").trim()

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
