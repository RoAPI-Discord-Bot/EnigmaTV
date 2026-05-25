package com.enigma.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class IptvRepository {
    private val client = OkHttpClient.Builder().build()

    private val playlists = listOf(
        "https://iptv-org.github.io/iptv/categories/sports.m3u" to "Sports",
        "https://iptv-org.github.io/iptv/categories/news.m3u" to "News",
        "https://iptv-org.github.io/iptv/countries/us.m3u" to "United States"
    )

    suspend fun loadChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        coroutineScope {
            playlists.map { (url, group) ->
                async {
                    runCatching {
                        val body = fetchText(url) ?: return@async emptyList()
                        M3uParser.parse(body, group)
                    }.getOrDefault(emptyList())
                }
            }.flatMap { it.await() }
        }.distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }

    fun search(channels: List<IptvChannel>, query: String): List<IptvChannel> {
        if (query.isBlank()) return channels
        val tokens = tokenize(query)
        return channels.filter { ch ->
            val hay = "${ch.name} ${ch.group}".lowercase()
            tokens.all { hay.contains(it) }
        }.sortedWith(
            compareByDescending<IptvChannel> { ch ->
                tokens.count { ch.name.lowercase().contains(it) }
            }.thenBy { it.name }
        )
    }

    private suspend fun fetchText(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "EnigmaTV/2.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    companion object {
        fun tokenize(query: String): List<String> {
            return query.lowercase()
                .replace("@", " ")
                .replace(" vs ", " ")
                .replace(" versus ", " ")
                .split(Regex("[\\s,./]+"))
                .map { it.trim() }
                .filter { it.length >= 2 }
        }
    }
}
