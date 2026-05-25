package com.enigma.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class StreamedRepository {
    private val api: StreamedApi = Retrofit.Builder()
        .baseUrl("https://streamed.pk/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(StreamedApi::class.java)

    private val sportSlugs = listOf(
        "baseball", "american-football", "football", "basketball", "hockey", "fight", "motor-sports"
    )

    suspend fun loadEvents(): List<LiveSportMatch> = coroutineScope {
        val live = async { runCatching { api.liveMatches().map { it.toMatch() } }.getOrDefault(emptyList()) } }
        val bySport = sportSlugs.map { sport ->
            async {
                runCatching { api.sportMatches(sport).map { it.toMatch() } }.getOrDefault(emptyList())
            }
        }
        (listOf(live.await()) + bySport.map { it.await() })
            .flatten()
            .distinctBy { it.id }
            .sortedByDescending { it.dateMs }
    }

    suspend fun fetchStreams(source: String, id: String): List<LiveStreamLink> {
        return api.streams(source, id).map { s ->
            LiveStreamLink(
                embedUrl = s.embedUrl,
                label = buildString {
                    append("Stream ${s.streamNo}")
                    if (s.language.isNotBlank()) append(" · ${s.language}")
                    if (s.hd) append(" · HD")
                },
                hd = s.hd,
                source = s.source.ifBlank { source }
            )
        }
    }

    fun search(events: List<LiveSportMatch>, query: String): List<LiveSportMatch> {
        if (query.isBlank()) return events
        val tokens = IptvRepository.tokenize(query)
        val expanded = expandSportsTokens(tokens)
        return events.filter { match ->
            val hay = match.title.lowercase() + " " + match.category.lowercase()
            expanded.all { hay.contains(it) }
        }.sortedWith(
            compareByDescending<LiveSportMatch> { m ->
                expanded.count { m.title.lowercase().contains(it) }
            }.thenByDescending { it.popular }
        )
    }

    private fun expandSportsTokens(tokens: List<String>): List<String> {
        val aliases = mapOf(
            "espn" to listOf("espn"),
            "mlb" to listOf("baseball", "mlb"),
            "nfl" to listOf("football", "american-football", "nfl"),
            "nba" to listOf("basketball", "nba"),
            "nhl" to listOf("hockey", "nhl"),
            "phillies" to listOf("phillies", "philadelphia"),
            "yankees" to listOf("yankees", "new york yankees"),
            "eagles" to listOf("eagles", "philadelphia"),
            "cowboys" to listOf("cowboys", "dallas"),
            "lakers" to listOf("lakers", "los angeles")
        )
        val out = tokens.toMutableList()
        tokens.forEach { t -> aliases[t]?.let { out.addAll(it) } }
        return out.distinct()
    }

    private fun StreamedMatchDto.toMatch() = LiveSportMatch(
        id = id,
        title = title,
        category = category,
        dateMs = date,
        posterPath = poster,
        sources = sources.map { MatchStreamSource(it.source, it.id) },
        popular = popular
    )
}
