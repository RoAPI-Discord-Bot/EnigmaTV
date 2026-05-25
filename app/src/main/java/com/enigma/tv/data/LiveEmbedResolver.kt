package com.enigma.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Unwraps live sports embed wrappers so WebView loads the real player page
 * (avoids sandbox iframe errors and raw JSON/API pages on streamed.pk).
 */
object LiveEmbedResolver {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(14, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    private val m3u8Regex = Regex(
        """(https?://[^\s"'\\<>]+\.m3u8[^\s"'\\<>]*)""",
        RegexOption.IGNORE_CASE
    )
    private val iframeSrcRegex = Regex(
        """<iframe[^>]+(?:src|data-src)=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val dataSrcRegex = Regex(
        """data-(?:src|iframe|url)=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val jsUrlRegex = Regex(
        """(?:file|src|source|url|embedUrl|playerUrl)\s*[:=]\s*["'](https?://[^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val jsonEmbedUrlRegex = Regex(
        """"embedUrl"\s*:\s*"([^"\\]+(?:\\.[^"\\]*)*)"""",
        RegexOption.IGNORE_CASE
    )

    private val WRAPPER_HINTS = listOf(
        "streamed.pk", "embedsports", "sandbox", "wrapper", "/redirect", "click."
    )

    suspend fun resolvePlayableUrl(embedUrl: String): String = withContext(Dispatchers.IO) {
        unwrapStreamApiResponse(embedUrl)?.let { unwrapped ->
            val resolved = resolveChain(unwrapped, depth = 0, visited = mutableSetOf())
            if (!looksLikeRawCodePage(resolved)) return@withContext resolved
        }
        val resolved = resolveChain(embedUrl, depth = 0, visited = mutableSetOf())
        if (looksLikeRawCodePage(resolved)) {
            fetchHtml(embedUrl)?.let { html ->
                pickBestEmbedFromBody(html, embedUrl)?.let { candidate ->
                    return@withContext resolveChain(candidate, depth = 0, visited = mutableSetOf())
                }
            }
        }
        resolved
    }

    /** If the streamed API URL returns JSON, extract the first real embed URL. */
    private suspend fun unwrapStreamApiResponse(url: String): String? {
        if (!looksLikeStreamApi(url)) return null
        val body = fetchHtml(url) ?: return null
        return pickBestEmbedFromBody(body, url)
    }

    private fun pickBestEmbedFromBody(body: String, baseUrl: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        val fromJson = jsonEmbedUrlRegex.findAll(body)
            .map { decode(it.groupValues[1]) }
            .filter { it.startsWith("http") && !looksLikeStreamApi(it) }
            .sortedByDescending { scorePlayerUrl(it) }
            .firstOrNull()
        if (fromJson != null) return fromJson
        return extractCandidates(body, baseUrl)
            .filter { !looksLikeStreamApi(it) }
            .maxByOrNull { scorePlayerUrl(it) }
    }

    private suspend fun resolveChain(url: String, depth: Int, visited: MutableSet<String>): String {
        if (depth > 5 || url in visited) return url
        visited.add(url)

        if (url.contains(".m3u8", ignoreCase = true)) return url

        StreamResolver.resolveDirectUrl(url)?.let { return it }

        val html = fetchHtml(url) ?: return url

        if (looksLikeRawCodePage(html)) {
            pickBestEmbedFromBody(html, url)?.let { candidate ->
                if (candidate != url) return resolveChain(candidate, depth + 1, visited)
            }
        }

        pickM3u8(html)?.let { return it }

        val candidates = extractCandidates(html, baseUrl = url)
            .filter { it != url && it !in visited && !looksLikeStreamApi(it) }
            .sortedByDescending { scorePlayerUrl(it) }

        for (candidate in candidates) {
            val next = resolveChain(candidate, depth + 1, visited)
            if (next != candidate || next.contains(".m3u8", true) || !looksLikeWrapper(next)) {
                return next
            }
            if (!looksLikeWrapper(next) && scorePlayerUrl(next) >= 4) return next
        }

        return url
    }

    private fun fetchHtml(pageUrl: String): String? = try {
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", StreamResolver.USER_AGENT)
            .header("Referer", pageUrl)
            .header("Accept", "text/html,application/xhtml+xml,application/json,*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    } catch (_: Exception) {
        null
    }

    private fun extractCandidates(html: String, baseUrl: String): List<String> {
        val found = linkedSetOf<String>()
        listOf(iframeSrcRegex, dataSrcRegex, jsUrlRegex).forEach { regex ->
            regex.findAll(html).forEach { m ->
                decode(m.groupValues[1]).takeIf { it.startsWith("http") }?.let { found.add(it) }
            }
        }
        m3u8Regex.findAll(html).forEach { m ->
            decode(m.groupValues[1]).let { found.add(it) }
        }
        return found.toList()
    }

    private fun pickM3u8(html: String): String? =
        m3u8Regex.find(html)?.groupValues?.get(1)?.let { decode(it) }

    private fun scorePlayerUrl(url: String): Int {
        val u = url.lowercase()
        var score = 0
        if (u.contains(".m3u8")) score += 20
        if (u.contains("embed")) score += 4
        if (u.contains("player")) score += 4
        if (u.contains("stream")) score += 3
        if (u.contains("liveembed")) score += 5
        if (u.contains("topembed")) score += 5
        if (u.contains("dlhd")) score += 5
        if (u.contains("sportsonline")) score += 5
        if (u.contains("casthill")) score += 4
        if (u.contains("givemereddit")) score += 4
        if (u.contains("ripplestream")) score += 4
        if (u.contains("vidsrc")) score += 4
        if (WRAPPER_HINTS.any { u.contains(it) }) score -= 3
        if (u.contains("sandbox")) score -= 8
        if (looksLikeStreamApi(u)) score -= 15
        return score
    }

    private fun looksLikeWrapper(url: String): Boolean {
        val u = url.lowercase()
        return WRAPPER_HINTS.any { u.contains(it) } && !u.contains(".m3u8")
    }

    private fun looksLikeStreamApi(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("/api/stream/") ||
            (u.contains("streamed.pk") && u.contains("/api/"))
    }

    fun isUnplayableContent(content: String): Boolean {
        val t = content.trim()
        if (t.startsWith("http", ignoreCase = true) && looksLikeStreamApi(t)) return true
        return looksLikeRawCodePage(content)
    }

    private fun looksLikeRawCodePage(content: String): Boolean {
        val t = content.trim().take(4000)
        if (t.startsWith("{") || t.startsWith("[")) return true
        if (t.contains("\"embedUrl\"") && !t.contains("<iframe", ignoreCase = true)) return true
        if (t.contains("sandbox") && t.length < 8000 && !t.contains("<video", ignoreCase = true)) return true
        return false
    }

    private fun decode(raw: String): String =
        raw.replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")
            .replace("&#x2F;", "/")
            .trim()
}
