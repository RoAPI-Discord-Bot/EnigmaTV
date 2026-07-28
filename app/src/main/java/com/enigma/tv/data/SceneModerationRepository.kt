package com.enigma.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class ModerationSceneInterval(
    val startMs: Long,
    val endMs: Long,
    val category: String, // "sexual", "nudity", "violence"
    val description: String = ""
)

object SceneModerationRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun getSceneIntervals(tmdbId: Int, type: ContentType, season: Int = 1, episode: Int = 1): List<ModerationSceneInterval> = withContext(Dispatchers.IO) {
        val intervals = mutableListOf<ModerationSceneInterval>()
        try {
            // Public OpenSkip / SponsorBlock-style Scene DB endpoint for media timestamps
            val url = if (type == ContentType.MOVIE) {
                "https://api.sponsor.ajay.app/v1/skipSegments?category=nudity&category=sexual&movieTmdbId=$tmdbId"
            } else {
                "https://api.sponsor.ajay.app/v1/skipSegments?category=nudity&category=sexual&showTmdbId=$tmdbId&season=$season&episode=$episode"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", StreamResolver.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val array = JSONArray(jsonStr)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val segment = obj.getJSONArray("segment")
                            val startSec = segment.getDouble(0)
                            val endSec = segment.getDouble(1)
                            val cat = obj.optString("category", "sexual")
                            intervals.add(
                                ModerationSceneInterval(
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    category = cat
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Silently fall back if network API unavailable
        }
        intervals
    }
}
