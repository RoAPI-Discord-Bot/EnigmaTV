package com.enigma.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Attempts to find a direct HLS/MP4 URL inside embed pages so movies/TV can use ExoPlayer
 * native ExoPlayer playback when possible.
 */
object StreamResolver {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val m3u8Regex = Regex("""(https?://[^\s"'\\<>]+\.m3u8[^\s"'\\<>]*)""", RegexOption.IGNORE_CASE)
    private val mp4Regex = Regex("""(https?://[^\s"'\\<>]+\.mp4[^\s"'\\<>]*)""", RegexOption.IGNORE_CASE)
    private val fileRegex = Regex("""["'](https?://[^"']+/file/[^"']+)["']""")
    private val sourceRegex = Regex("""source:\s*['"](https?://[^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val fileJsonRegex = Regex("""file:\s*['"](https?://[^'"]+)['"]""", RegexOption.IGNORE_CASE)

    suspend fun resolveDirectUrl(embedUrl: String): String? = withContext(Dispatchers.IO) {
        if (embedUrl.contains(".m3u8", ignoreCase = true)) return@withContext clean(embedUrl)
        if (embedUrl.contains(".mp4", ignoreCase = true) && !embedUrl.contains("embed")) {
            return@withContext clean(embedUrl)
        }
        try {
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", embedUrl)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null
                m3u8Regex.find(html)?.groupValues?.get(1)?.let { return@withContext clean(it) }
                mp4Regex.find(html)?.groupValues?.get(1)?.let { return@withContext clean(it) }
                fileRegex.find(html)?.groupValues?.get(1)?.let { return@withContext clean(it) }
                sourceRegex.find(html)?.groupValues?.get(1)?.let { return@withContext clean(it) }
                fileJsonRegex.find(html)?.groupValues?.get(1)?.let { return@withContext clean(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun clean(url: String): String =
        url.replace("\\/", "/").replace("\\u0026", "&").trim()

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
