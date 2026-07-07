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
            // ── Round 1: VidLink API vs primary WebView ──────────────────────────────
            val apiJob = async {
                Log.d(TAG, "[$tmdbId] Round1/VidLink: starting")
                val r = when (type) {
                    ContentType.MOVIE -> VidLinkResolver.resolveMovie(tmdbId)
                    ContentType.TV    -> VidLinkResolver.resolveTv(tmdbId, season, episode)
                }
                Log.d(TAG, "[$tmdbId] Round1/VidLink: ${if (r != null) "got stream" else "null"}")
                r
            }

            val webViewJob = async {
                if (activity == null) return@async null
                Log.d(TAG, "[$tmdbId] Round1/WebView: $primaryEmbedUrl")
                val r = withTimeoutOrNull(15_000) {
                    StreamExtractor(context).extractStreamUrl(primaryEmbedUrl, activity = activity)
                }
                Log.d(TAG, "[$tmdbId] Round1/WebView: ${if (r != null) "got stream" else "null"}")
                r
            }

            // VidLink API is fast (< 2s) — wait for it first.
            // If it has the content, we get high-quality right away.
            val apiResult = withTimeoutOrNull(6_000) { apiJob.await() }
            if (apiResult != null) {
                Log.d(TAG, "[$tmdbId] VidLink API won")
                if (webViewJob.isActive) webViewJob.cancel()
                return@coroutineScope apiResult
            }

            // VidLink didn't have it (403 / missing content). Wait for WebView.
            Log.d(TAG, "[$tmdbId] VidLink API failed — waiting for WebView")
            val webResult = webViewJob.await()
            if (apiJob.isActive) apiJob.cancel()
            if (webResult != null) {
                Log.d(TAG, "[$tmdbId] Primary WebView won")
                return@coroutineScope webResult
            }

            // ── Round 2: Try remaining embed sources sequentially ────────────────────
            if (activity != null && embedUrls.size > 1) {
                Log.d(TAG, "[$tmdbId] Round1 failed — trying ${embedUrls.size - 1} fallback sources")

                for ((srcName, fallbackUrl) in embedUrls.drop(1)) {
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
