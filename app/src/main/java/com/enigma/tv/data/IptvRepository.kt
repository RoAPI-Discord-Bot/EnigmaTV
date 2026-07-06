package com.enigma.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class IptvRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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

    fun channelGroups(channels: List<IptvChannel>): List<String> =
        channels.map { it.group }.distinct().sorted()

    fun groupChannels(channels: List<IptvChannel>): Map<String, List<IptvChannel>> =
        channels.groupBy { it.group }.toSortedMap()

    fun filterByGroup(channels: List<IptvChannel>, group: String?): List<IptvChannel> {
        if (group.isNullOrBlank()) return channels
        return channels.filter { it.group == group }
    }

    fun resolveByIds(channels: List<IptvChannel>, ids: Collection<String>): List<IptvChannel> {
        if (ids.isEmpty()) return emptyList()
        val map = channels.associateBy { it.id }
        return ids.mapNotNull { map[it] }
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
