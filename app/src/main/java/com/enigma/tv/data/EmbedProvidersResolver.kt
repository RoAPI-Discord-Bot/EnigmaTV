package com.enigma.tv.data

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "EmbedResolver"

/**
 * Scores a resolved stream so we can pick the best one from concurrent races.
 *
 * Points:
 *   +40  HLS  (adaptive, no 403 expiry risk)
 *   +20  has subtitle track
 *   +10  VidLink API source (usually faster CDN)
 *
 * A higher score = better for the user.
 */
private fun ResolvedStream.quality(): Int {
    var score = 0
    val lower = url.lowercase()
    if (lower.contains(".m3u8") || lower.contains("playlist") || lower.contains("/master.")) score += 40
    if (!subtitleUrl.isNullOrBlank()) score += 20
    if (provider.contains("vidlink", ignoreCase = true) || provider.contains("api", ignoreCase = true)) score += 10
    return score
}

private fun ResolvedStream.isHls(): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains("playlist") || lower.contains("/master.")
}

private fun ResolvedStream.isExpiredMp4(): Boolean {
    if (isHls()) return false
    val tParam = Regex("""[?&]t=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: return false
    return System.currentTimeMillis() / 1000L > tParam
}

/**
 * Resolves a playable stream by:
 *
 *  Phase 1 — Fire ALL sources concurrently (VidLink API + ALL WebView sources).
 *             Collect results for up to [COLLECTION_WINDOW_MS].
 *             As soon as we get the first HLS stream with a subtitle, we return
 *             immediately without waiting further (best case ~600ms).
 *
 *  Phase 2 — After the collection window, pick the highest-scoring stream from
 *             whatever came in: HLS beats MP4, subtitle beats none.
 *
 *  This eliminates the "pick one provider and cross your fingers" problem.
 *  Every provider races in parallel and the best result wins.
 */
object EmbedProvidersResolver {

    /** How long to wait collecting results before picking the best one. */
    private const val COLLECTION_WINDOW_MS = 5_000L

    /** If a perfect stream (HLS + subtitle) arrives, return immediately after this delay. */
    private const val EARLY_WIN_DELAY_MS = 100L

    suspend fun resolveFromAllProviders(
        context: Context,
        activity: Activity?,
        tmdbId: Int,
        type: ContentType,
        season: Int,
        episode: Int,
        preferredEmbedUrl: String? = null,
        onStatus: (String) -> Unit = {}
    ): ResolvedStream? {
        val embedUrls = buildEmbedList(tmdbId, type, season, episode, preferredEmbedUrl)
        if (embedUrls.isEmpty()) return null

        return coroutineScope {
            // Unlimited channel — all concurrent results land here
            val resultsChannel = Channel<Pair<String, ResolvedStream?>>(capacity = Channel.UNLIMITED)

            val allJobs = mutableListOf<kotlinx.coroutines.Job>()

            onStatus("Searching for stream...")

            // ── VidLink direct API (fast JSON, ~400ms) ────────────────────────
            allJobs += launch {
                Log.d(TAG, "[$tmdbId] Concurrent/VidLinkAPI: starting")
                val r = when (type) {
                    ContentType.MOVIE -> VidLinkResolver.resolveMovie(tmdbId)
                    ContentType.TV    -> VidLinkResolver.resolveTv(tmdbId, season, episode)
                }
                Log.d(TAG, "[$tmdbId] Concurrent/VidLinkAPI: ${if (r != null) "got stream" else "null"}")
                if (r != null) onStatus("Source found. Preparing playback...")
                resultsChannel.send("VidLink API" to r)
            }

            // ── All WebView sources concurrently ──────────────────────────────
            val sourcesToRace = embedUrls.take(5)
            sourcesToRace.forEach { (srcName, url) ->
                allJobs += launch {
                    if (activity == null) {
                        resultsChannel.send(srcName to null)
                        return@launch
                    }
                    Log.d(TAG, "[$tmdbId] Concurrent/WebView: starting $srcName")
                    val r = withTimeoutOrNull(COLLECTION_WINDOW_MS + 1_000) {
                        StreamExtractor(context).extractStreamUrl(url, activity = activity)
                    }
                    Log.d(TAG, "[$tmdbId] Concurrent/WebView: $srcName ${if (r != null) "got stream" else "null"}")
                    if (r != null) onStatus("Source found. Preparing playback...")
                    resultsChannel.send(srcName to r)
                }
            }

            val totalExpected = 1 + sourcesToRace.size
            val collected = mutableListOf<Pair<String, ResolvedStream>>()
            var receivedCount = 0
            var foundSubtitle: String? = null

            // Collect results within the time window
            val deadline = System.currentTimeMillis() + COLLECTION_WINDOW_MS

            while (receivedCount < totalExpected) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) {
                    if (collected.isNotEmpty()) onStatus("Stream found — starting player...")
                    break
                }

                val received = withTimeoutOrNull(remaining) { resultsChannel.receive() }
                    ?: break  // window expired

                val (srcName, stream) = received
                receivedCount++

                if (stream?.subtitleUrl != null && foundSubtitle == null) {
                    foundSubtitle = stream.subtitleUrl
                }

                if (stream != null && !stream.isExpiredMp4()) {
                    Log.d(TAG, "[$tmdbId] $srcName → score=${stream.quality()} hls=${stream.isHls()} sub=${stream.subtitleUrl != null}")
                    collected.add(srcName to stream)

                    // Early win: if we have a perfect HLS+subtitle, no need to wait more
                    val best = collected.maxByOrNull { it.second.quality() }!!.second
                    if (best.isHls() && best.subtitleUrl != null) {
                        Log.d(TAG, "[$tmdbId] Early win: HLS+subtitle from ${collected.last().first}")
                        onStatus("Stream ready — loading player...")
                        // Small delay to let any simultaneous subtitle-less result also arrive
                        kotlinx.coroutines.delay(EARLY_WIN_DELAY_MS)
                        allJobs.forEach { it.cancel() }
                        resultsChannel.close()
                        return@coroutineScope pickBest(tmdbId, collected, foundSubtitle)
                    }
                } else if (stream != null) {
                    Log.w(TAG, "[$tmdbId] $srcName MP4 is EXPIRED — skipping")
                }
            }

            // Cancel remaining and close channel
            allJobs.forEach { it.cancel() }
            resultsChannel.close()

            if (collected.isNotEmpty()) {
                onStatus("Stream found — starting player...")
                return@coroutineScope pickBest(tmdbId, collected, foundSubtitle)
            }

            // ── Fallback: sequential pass through remaining sources ───────────
            if (activity != null && embedUrls.size > sourcesToRace.size) {
                Log.d(TAG, "[$tmdbId] No results — trying ${embedUrls.size - sourcesToRace.size} remaining sources")
                onStatus("Trying alternate sources...")
                for ((srcName, fallbackUrl) in embedUrls.drop(sourcesToRace.size)) {
                    Log.d(TAG, "[$tmdbId] Fallback: $srcName")
                    val res = withTimeoutOrNull(10_000) {
                        StreamExtractor(context).extractStreamUrl(fallbackUrl, activity = activity)
                    }
                    if (res != null && !res.isExpiredMp4()) {
                        Log.d(TAG, "[$tmdbId] Fallback $srcName: SUCCESS score=${res.quality()}")
                        onStatus("Source found. Starting playback...")
                        return@coroutineScope if (res.subtitleUrl == null && foundSubtitle != null) {
                            res.copy(subtitleUrl = foundSubtitle)
                        } else res
                    }
                    if (res?.subtitleUrl != null && foundSubtitle == null) {
                        foundSubtitle = res.subtitleUrl
                    }
                    Log.d(TAG, "[$tmdbId] Fallback $srcName: failed")
                }
            }

            Log.w(TAG, "[$tmdbId] All sources exhausted — content not found")
            null
        }
    }

    private fun pickBest(tmdbId: Int, results: List<Pair<String, ResolvedStream>>, fallbackSubtitle: String?): ResolvedStream {
        val best = results.maxByOrNull { it.second.quality() }!!
        val stream = best.second
        val finalStream = if (stream.subtitleUrl == null && fallbackSubtitle != null) {
            stream.copy(subtitleUrl = fallbackSubtitle)
        } else stream
        Log.d(TAG, "[$tmdbId] WINNER: ${best.first} score=${finalStream.quality()} hls=${finalStream.isHls()} sub=${finalStream.subtitleUrl != null}")
        return finalStream
    }

    private fun buildEmbedList(
        tmdbId: Int,
        type: ContentType,
        season: Int,
        episode: Int,
        preferredEmbedUrl: String?
    ): List<Pair<String, String>> {
        val seenUrls = mutableSetOf<String>()
        val list = mutableListOf<Pair<String, String>>()
        fun addIfNew(name: String, url: String) {
            if (seenUrls.add(url)) list.add(name to url)
        }
        if (!preferredEmbedUrl.isNullOrBlank()) {
            addIfNew("Current", preferredEmbedUrl)
        }
        when (type) {
            ContentType.MOVIE -> StreamSources.movieSources.forEach { src ->
                addIfNew(src.name, src.movieUrl(tmdbId))
            }
            ContentType.TV -> StreamSources.tvSources.forEach { src ->
                addIfNew(src.name, src.tvUrl(tmdbId, season, episode))
            }
        }
        return list
    }
}
