package com.enigma.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class StreamedRepository {
    companion object {
        private val STREAM_API_PATH = Regex("""/api/stream/([^/]+)/([^/?#]+)""", RegexOption.IGNORE_CASE)

        fun embedCandidates(raw: String): List<String> {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return emptyList()
            val apiMatch = STREAM_API_PATH.find(trimmed)
            if (apiMatch != null) {
                val src = apiMatch.groupValues[1]
                val mid = apiMatch.groupValues[2]
                return listOf(
                    "https://embedsports.top/embed/$src/$mid/1",
                    "https://embedsports.top/embed/$src/$mid",
                    "https://streamed.pk/watch/$src/$mid",
                    "https://streamed.pk/embed/$src/$mid",
                    "https://dlhd.dad/watch/$src-$mid"
                )
            }
            val single = if (!trimmed.startsWith("http")) "https://streamed.pk$trimmed" else trimmed
            return listOf(single)
        }

        fun normalizeStreamEmbed(raw: String): String = embedCandidates(raw).firstOrNull().orEmpty()

        fun bumpStreamNumber(embedUrl: String): String? {
            val match = Regex("""/(\d+)/?$""").find(embedUrl.trimEnd('/')) ?: return null
            val num = match.groupValues[1].toIntOrNull() ?: return null
            val next = (num % 6) + 1
            return embedUrl.replace(Regex("""/\d+/?$"""), "/$next")
        }
    }

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
        val jobs = buildList {
            add(async {
                runCatching { api.liveMatches().map { it.toMatch() } }.getOrDefault(emptyList())
            })
            sportSlugs.forEach { sport ->
                add(async {
                    runCatching { api.sportMatches(sport).map { it.toMatch() } }.getOrDefault(emptyList())
                })
            }
        }
        jobs.awaitAll()
            .flatten()
            .distinctBy { it.id }
            .sortedByDescending { it.dateMs }
    }

    suspend fun fetchStreams(source: String, id: String): List<LiveStreamLink> {
        return api.streams(source, id).map { s ->
            LiveStreamLink(
                embedUrl = normalizeStreamEmbed(s.embedUrl),
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
