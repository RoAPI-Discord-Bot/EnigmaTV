package com.enigma.tv.data

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
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
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
            parseApiResponse(get(apiUrl))?.let { return ResolvedStream.vidLink(it) }
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

    private fun resolveViaPage(pageUrl: String): ResolvedStream? {
        parseStreamFromBody(get(pageUrl))?.let { return ResolvedStream.vidLink(it) }
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

    private fun parseApiResponse(body: String?): String? = parseStreamFromBody(body)

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

    private fun parseStreamFromBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        pickUrl(m3u8Regex.find(body)?.groupValues?.get(1))?.let { return it }
        pickUrl(mp4Regex.find(body)?.groupValues?.get(1))?.let { return it }
        return try {
            val el = JsonParser.parseString(body)
            findUrlInJson(el)
        } catch (_: Exception) {
            null
        }
    }

    private fun findUrlInJson(el: JsonElement): String? {
        when {
            el.isJsonObject -> {
                val obj = el.asJsonObject
                listOf("url", "stream", "file", "hls", "source", "src", "link", "m3u8", "playlist")
                    .forEach { key ->
                        if (obj.has(key)) {
                            pickUrl(jsonPrimitiveUrl(obj.get(key)))?.let { return it }
                        }
                    }
                obj.entrySet().forEach { (_, v) ->
                    findUrlInJson(v)?.let { return it }
                }
            }
            el.isJsonArray -> el.asJsonArray.forEach { item ->
                findUrlInJson(item)?.let { return it }
            }
            el.isJsonPrimitive -> pickUrl(jsonPrimitiveUrl(el))?.let { return it }
        }
        return null
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
