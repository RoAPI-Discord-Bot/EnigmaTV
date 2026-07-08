package com.enigma.tv.data

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * VidLink direct API (enc-dec.app + /api/b/) — same flow used by community VidLink proxies.
 */
object VidLinkResolver {
    private const val TAG = "VidLinkResolver"
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val m3u8Regex = Regex("""(https?://[^\s"'\\<>]+\.m3u8[^\s"'\\<>]*)""", RegexOption.IGNORE_CASE)
    private val mp4Regex = Regex("""(https?://[^\s"'\\<>]+\.mp4[^\s"'\\<>]*)""", RegexOption.IGNORE_CASE)

    private val headers = mapOf(
        "User-Agent" to StreamResolver.USER_AGENT,
        "Referer" to "https://vidlink.pro/",
        "Origin" to "https://vidlink.pro",
        "Accept" to "application/json, text/plain, */*"
    )

    suspend fun resolveMovie(tmdbId: Int): ResolvedStream? = withContext(Dispatchers.IO) {
        resolveViaApi("movie", tmdbId, null, null)
            ?: resolveViaPage("https://vidlink.pro/movie/$tmdbId")
    }

    suspend fun resolveTv(tmdbId: Int, season: Int, episode: Int): ResolvedStream? = withContext(Dispatchers.IO) {
        resolveViaApi("tv", tmdbId, season, episode)
            ?: resolveViaPage("https://vidlink.pro/tv/$tmdbId/$season/$episode")
    }

    private fun resolveViaApi(
        type: String,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): ResolvedStream? {
        val encrypted = fetchEncryptedId(tmdbId) ?: return null
        val paths = buildList {
            when (type) {
                "movie" -> {
                    add("https://vidlink.pro/api/b/movie/$encrypted")
                    add("https://vidlink.pro/api/movie/$encrypted")
                }
                else -> {
                    add("https://vidlink.pro/api/b/tv/$encrypted/$season/$episode")
                    add("https://vidlink.pro/api/tv/$encrypted/$season/$episode")
                }
            }
        }
        for (apiUrl in paths) {
            parseApiResponse(get(apiUrl))?.let { (stream, sub) ->
                Log.i(TAG, "resolveViaApi: picked url=$stream sub=$sub")
                return ResolvedStream.vidLink(stream).copy(subtitleUrl = sub)
            }
        }
        return null
    }

    private fun fetchEncryptedId(tmdbId: Int): String? {
        val endpoints = listOf(
            "https://enc-dec.app/api/enc-vidlink?text=$tmdbId",
            "https://enc-dec.app/api/enc-vidlink?id=$tmdbId"
        )
        for (url in endpoints) {
            parseEncDecResult(get(url))?.let { return it }
        }
        return null
    }

    private suspend fun resolveViaPage(url: String): ResolvedStream? {
        val body = get(url) ?: return null
        parseEncDecResult(body)?.let { parseApiResponse(it) }?.let { (stream, sub) ->
            return ResolvedStream.fromEmbed(url, stream, "embed-hls").copy(subtitleUrl = sub)
        }
        parseStreamFromBody(body)?.let { (stream, sub) ->
            return ResolvedStream.fromEmbed(url, stream, "embed-hls").copy(subtitleUrl = sub)
        }
        return null
    }

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (_: Exception) {
        null
    }

    private fun parseApiResponse(body: String?): Pair<String, String?>? = parseStreamFromBody(body)

    private fun parseEncDecResult(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (root.has("status") && root.get("status").asInt != 200) return null
            when {
                root.has("result") && root.get("result").isJsonPrimitive -> root.get("result").asString
                root.has("text") && root.get("text").isJsonPrimitive -> root.get("text").asString
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStreamFromBody(body: String?): Pair<String, String?>? {
        if (body.isNullOrBlank()) return null
        // Try JSON scoring FIRST so we pick the best-quality URL when multiple are present.
        // Only fall back to regex if JSON parsing fails (e.g. the response is plain text).
        val jsonResult = try {
            val el = JsonParser.parseString(body)
            findUrlInJson(el)
        } catch (_: Exception) {
            null
        }
        if (jsonResult != null) {
            Log.d(TAG, "parseStreamFromBody: JSON path picked ${jsonResult.first}")
            return jsonResult
        }
        // Plain-text fallback — find ALL regex matches, score them, pick best
        val m3u8Matches = m3u8Regex.findAll(body).mapNotNull { pickUrl(it.groupValues[1]) }.toList()
        val mp4Matches  = mp4Regex.findAll(body).mapNotNull { pickUrl(it.groupValues[1]) }.toList()
        val all = (m3u8Matches + mp4Matches).distinctBy { it }
        if (all.isEmpty()) return null
        val best = all.maxByOrNull { scoreUrl(it) }!!
        Log.d(TAG, "parseStreamFromBody: regex path picked $best from ${all.size} candidates")
        return best to null
    }

    private fun findUrlInJson(el: JsonElement): Pair<String, String?>? {
        // Collect all stream URLs found, then pick the best (master playlist preferred)
        val candidates = mutableListOf<String>()
        var subUrl: String? = null
        collectUrlsFromJson(el, candidates) { sub -> if (subUrl == null) subUrl = sub }

        if (candidates.isEmpty()) return null

        // Score candidates: master/adaptive HLS > mp4 > quality-specific HLS
        val best = candidates.maxByOrNull { scoreUrl(it) } ?: return null
        return best to subUrl
    }

    private fun scoreUrl(url: String): Int {
        val lower = url.lowercase()
        if (!lower.contains(".m3u8") && !lower.contains(".mp4")) return 0
        // index.m3u8 is a quality-specific segment playlist (e.g. 360p/index.m3u8), NOT a master
        if (lower.contains("master.m3u8") || lower.contains("playlist.m3u8")) return 100
        if (lower.contains(".m3u8") && !hasQualitySuffix(lower)) return 90
        if (lower.contains(".mp4")) return 50
        return 10 // quality-specific .m3u8
    }

    private fun hasQualitySuffix(lower: String): Boolean {
        return lower.contains("360p") || lower.contains("480p") || lower.contains("720p") ||
               lower.contains("1080p") || lower.contains("2160p") || lower.contains("4k") ||
               lower.contains("/360/") || lower.contains("/480/") || lower.contains("/720/") ||
               lower.contains("/1080/") || lower.contains("/sd/") || lower.contains("/hd/")
    }

    private fun collectUrlsFromJson(
        el: JsonElement,
        urls: MutableList<String>,
        onSub: (String) -> Unit
    ) {
        when {
            el.isJsonObject -> {
                val obj = el.asJsonObject

                listOf("url", "stream", "file", "hls", "source", "src", "link", "m3u8", "playlist")
                    .forEach { key ->
                        if (obj.has(key)) {
                            pickUrl(jsonPrimitiveUrl(obj.get(key)))?.let { urls.add(it) }
                        }
                    }

                listOf("subtitles", "tracks", "captions").forEach { key ->
                    if (obj.has(key) && obj.get(key).isJsonArray) {
                        val subs = obj.getAsJsonArray(key)
                        for (item in subs) {
                            if (item.isJsonObject) {
                                val subObj = item.asJsonObject
                                val lang = subObj.get("language")?.asString ?: subObj.get("lang")?.asString ?: subObj.get("label")?.asString ?: ""
                                if (lang.contains("en", ignoreCase = true) || lang.contains("english", ignoreCase = true)) {
                                    val sub = subObj.get("url")?.asString ?: subObj.get("file")?.asString
                                    if (sub != null && sub.contains(".vtt", true)) onSub(sub)
                                }
                            }
                        }
                    }
                }

                obj.entrySet().forEach { (_, v) -> collectUrlsFromJson(v, urls, onSub) }
            }
            el.isJsonArray -> el.asJsonArray.forEach { item -> collectUrlsFromJson(item, urls, onSub) }
            el.isJsonPrimitive -> pickUrl(jsonPrimitiveUrl(el))?.let { urls.add(it) }
        }
    }

    private fun jsonPrimitiveUrl(el: JsonElement): String? =
        if (el.isJsonPrimitive) el.asString else null

    private fun pickUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = clean(raw)
        if (cleaned.contains(".m3u8", true) || cleaned.contains(".mp4", true)) return cleaned
        return null
    }

    private fun clean(url: String): String =
        url.replace("\\/", "/").replace("\\u0026", "&").trim()
}
