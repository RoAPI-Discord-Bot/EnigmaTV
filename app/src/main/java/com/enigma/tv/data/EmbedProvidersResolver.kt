package com.enigma.tv.data

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

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
                val r = StreamExtractor(context).extractStreamUrl(primaryEmbedUrl, activity = activity)
                Log.d(TAG, "[$tmdbId] Round1/WebView: ${if (r != null) "got stream" else "null"}")
                r
            }

            var result: ResolvedStream? = null
            try {
                select<Unit> {
                    apiJob.onAwait { r ->
                        if (r != null) {
                            result = r
                            if (r.subtitleUrl == null) {
                                // API doesn't provide subtitles directly. Give the WebView a few seconds
                                // to catch the subtitles before we cancel it and play without them.
                                val webResult = kotlinx.coroutines.withTimeoutOrNull(3500) { webViewJob.await() }
                                if (webResult?.subtitleUrl != null) {
                                    result = r.copy(subtitleUrl = webResult.subtitleUrl)
                                }
                            }
                            webViewJob.cancel()
                        } else {
                            result = webViewJob.await()
                        }
                    }
                    webViewJob.onAwait { r ->
                        if (r != null) { result = r; apiJob.cancel() }
                        else           { result = apiJob.await() }
                    }
                }
            } finally {
                if (apiJob.isActive)     apiJob.cancel()
                if (webViewJob.isActive) webViewJob.cancel()
            }

            // ── Round 2: Try remaining embed sources sequentially ────────────────────
            if (result == null && activity != null) {
                Log.d(TAG, "[$tmdbId] Round1 failed — trying ${embedUrls.size - 1} fallback sources")
                for (i in 1 until embedUrls.size) {
                    val (srcName, fallbackUrl) = embedUrls[i]
                    Log.d(TAG, "[$tmdbId] Fallback[$i] $srcName: $fallbackUrl")
                    result = StreamExtractor(context).extractStreamUrl(fallbackUrl, activity = activity)
                    if (result != null) {
                        Log.d(TAG, "[$tmdbId] Fallback[$i] $srcName: SUCCESS")
                        break
                    }
                    Log.d(TAG, "[$tmdbId] Fallback[$i] $srcName: failed")
                }
            }

            if (result == null) Log.w(TAG, "[$tmdbId] All sources exhausted — content not found")
            result
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
