package com.enigma.tv.data

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "EmbedResolver"

/**
 * Resolves a playable stream by:
 *
 *   Round 1 — Race VidLink direct API vs hidden WebView on primary embed URL.
 *             Whichever returns a non-null stream first wins.
 *
 *   Round 2 — If Round 1 fails (both return null), try ALL remaining embed
 *             sources sequentially via WebView until one works.
 *             This handles newer/obscure content that isn't on VidLink yet.
 */
object EmbedProvidersResolver {

    suspend fun resolveFromAllProviders(
        context: Context,
        activity: Activity?,
        tmdbId: Int,
        type: ContentType,
        season: Int,
        episode: Int,
        preferredEmbedUrl: String? = null
    ): ResolvedStream? {
        val embedUrls = buildEmbedList(tmdbId, type, season, episode, preferredEmbedUrl)
        val primaryEmbedUrl = embedUrls.firstOrNull()?.second ?: return null

        return coroutineScope {
            val resultsChannel = kotlinx.coroutines.channels.Channel<Pair<String, ResolvedStream?>>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)

            // ── Round 1: Race VidLink API vs Top 3 WebViews concurrently ──────────────────────────────
            val apiJob = launch {
                Log.d(TAG, "[$tmdbId] Concurrent/VidLinkAPI: starting")
                val r = when (type) {
                    ContentType.MOVIE -> VidLinkResolver.resolveMovie(tmdbId)
                    ContentType.TV    -> VidLinkResolver.resolveTv(tmdbId, season, episode)
                }
                Log.d(TAG, "[$tmdbId] Concurrent/VidLinkAPI: ${if (r != null) "got stream" else "null"}")
                resultsChannel.send("VidLink API" to r)
            }

            val topSources = embedUrls.take(3)
            val webJobs = topSources.map { (srcName, url) ->
                launch {
                    if (activity == null) {
                        resultsChannel.send(srcName to null)
                        return@launch
                    }
                    Log.d(TAG, "[$tmdbId] Concurrent/WebView: starting $srcName")
                    val r = withTimeoutOrNull(15_000) {
                        StreamExtractor(context).extractStreamUrl(url, activity = activity)
                    }
                    Log.d(TAG, "[$tmdbId] Concurrent/WebView: $srcName ${if (r != null) "got stream" else "null"}")
                    resultsChannel.send(srcName to r)
                }
            }

            val totalExpected = 1 + topSources.size
            var bestMp4: ResolvedStream? = null
            var finalResult: ResolvedStream? = null
            var receivedCount = 0

            while (receivedCount < totalExpected) {
                val (sourceName, stream) = resultsChannel.receive()
                receivedCount++

                if (stream != null) {
                    val isHls = stream.url.contains(".m3u8", ignoreCase = true)
                    if (isHls) {
                        Log.d(TAG, "[$tmdbId] $sourceName WON (HLS). Cancelling others.")
                        finalResult = stream
                        break
                    } else {
                        Log.d(TAG, "[$tmdbId] $sourceName returned MP4. Saving as fallback.")
                        if (bestMp4 == null) bestMp4 = stream
                    }
                }
            }

            // Clean up: cancel any unfinished background jobs
            apiJob.cancel()
            webJobs.forEach { it.cancel() }
            resultsChannel.close()

            if (finalResult != null) {
                return@coroutineScope finalResult
            }

            if (bestMp4 != null) {
                Log.d(TAG, "[$tmdbId] All top sources finished without HLS. Falling back to MP4.")
                return@coroutineScope bestMp4
            }

            // ── Round 2: Try remaining embed sources sequentially ────────────────────
            if (activity != null && embedUrls.size > 3) {
                Log.d(TAG, "[$tmdbId] Top 3 failed — trying ${embedUrls.size - 3} fallback sources")

                for ((srcName, fallbackUrl) in embedUrls.drop(3)) {
                    Log.d(TAG, "[$tmdbId] Fallback: $srcName")
                    val res = withTimeoutOrNull(12_000) {
                        StreamExtractor(context).extractStreamUrl(fallbackUrl, activity = activity)
                    }
                    if (res != null) {
                        Log.d(TAG, "[$tmdbId] Fallback $srcName: SUCCESS")
                        return@coroutineScope res
                    }
                    Log.d(TAG, "[$tmdbId] Fallback $srcName: failed")
                }
            }

            Log.w(TAG, "[$tmdbId] All sources exhausted — content not found")
            null
        }
    }

    private fun buildEmbedList(
        tmdbId: Int,
        type: ContentType,
        season: Int,
        episode: Int,
        preferredEmbedUrl: String?
    ): List<Pair<String, String>> {
        val list = linkedSetOf<Pair<String, String>>()
        if (!preferredEmbedUrl.isNullOrBlank()) {
            list.add("Current" to preferredEmbedUrl)
        }
        when (type) {
            ContentType.MOVIE -> StreamSources.movieSources.forEach { src ->
                list.add(src.name to src.movieUrl(tmdbId))
            }
            ContentType.TV -> StreamSources.tvSources.forEach { src ->
                list.add(src.name to src.tvUrl(tmdbId, season, episode))
            }
        }
        return list.toList()
    }
}
