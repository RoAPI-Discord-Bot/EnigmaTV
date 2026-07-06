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

        fun formatEventSchedule(dateMs: Long): String {
            if (dateMs <= 0L) return ""
            val delta = dateMs - System.currentTimeMillis()
            val totalMin = kotlin.math.abs(delta / 60_000L).toInt()
            val hours = totalMin / 60
            val mins = totalMin % 60
            return when {
                delta in -4 * 3_600_000L..30 * 60_000L -> "LIVE NOW"
                delta > 0L && hours > 0 -> "Starts in ${hours}h ${mins}m"
                delta > 0L -> "Starts in ${mins}m"
                hours > 0 -> "Started ${hours}h ago"
                else -> "Started ${mins}m ago"
            }
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

    suspend fun loadEvents(): List<LiveSportMatch> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val allMatches = mutableListOf<LiveSportMatch>()
        
        // Fetch live matches first (most important)
        runCatching { allMatches.addAll(api.liveMatches().map { it.toMatch() }) }
        
        // Fetch specific sports sequentially to prevent Cloudflare rate limits (429)
        for (sport in sportSlugs) {
            kotlinx.coroutines.delay(250) // gentle throttle
            runCatching { allMatches.addAll(api.sportMatches(sport).map { it.toMatch() }) }
        }
        
        val now = System.currentTimeMillis()
        val windowMs = 12 * 3_600_000L  // show games within 12-hour future window
        
        allMatches
            // Drop games more than 12h in the future — they clutter the top
            .filter { it.dateMs <= now + windowMs }
            .sortedBy { kotlin.math.abs(it.dateMs - now) }
            .distinctBy { it.title.substringBefore(" ·").trim() }
            .map { labelEventSchedule(it) }
            // Sort: LIVE NOW first, then upcoming soonest, then recently started
            .sortedWith(
                compareByDescending<LiveSportMatch> { m ->
                    when {
                        m.dateMs in (now - 4 * 3_600_000L)..now -> 2  // live or just started
                        m.dateMs > now -> 1                            // upcoming soon
                        else -> 0                                       // already over
                    }
                }.thenBy { m ->
                    when {
                        m.dateMs > now -> m.dateMs          // upcoming: soonest first
                        else -> -m.dateMs                   // past: most recent first
                    }
                }
            )
    }

    /** Keep duplicate matchups (today vs tomorrow) but label them clearly in the list. */
    private fun labelEventSchedule(match: LiveSportMatch): LiveSportMatch {
        val schedule = formatEventSchedule(match.dateMs)
        if (schedule.isBlank() || schedule == "LIVE NOW") return match
        val tag = when {
            schedule.startsWith("Starts") -> schedule
            else -> schedule
        }
        val base = match.title.substringBefore(" · Starts").substringBefore(" · LIVE").trim()
        return match.copy(title = "$base · $tag")
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
