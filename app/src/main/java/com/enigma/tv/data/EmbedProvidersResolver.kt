package com.enigma.tv.data

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/**
 * Resolves a playable stream by racing two proven paths simultaneously:
 *
 *   Path A — VidLink direct API (fast JSON, ~0.5–2s when working)
 *   Path B — Hidden WebView extractor on the primary embed URL
 *             (runs real JS like a browser, catches the .m3u8 when API fails)
 *
 * Whichever path returns first wins; the other is immediately cancelled.
 * No HTTP scraping — it almost never works on JS-obfuscated providers and just wastes time.
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
        // Build ordered list of embed URLs to try in WebView
        val embedUrls = buildEmbedList(tmdbId, type, season, episode, preferredEmbedUrl)
        val primaryEmbedUrl = embedUrls.firstOrNull()?.second ?: return null

        return coroutineScope {
            // Path A: VidLink direct API
            val apiJob = async {
                when (type) {
                    ContentType.MOVIE -> VidLinkResolver.resolveMovie(tmdbId)
                    ContentType.TV -> VidLinkResolver.resolveTv(tmdbId, season, episode)
                }
            }

            // Path B: Hidden WebView on primary embed URL (requires activity)
            val webViewJob = async {
                if (activity == null) return@async null
                StreamExtractor(context).extractStreamUrl(primaryEmbedUrl, activity = activity)
            }

            // Race them — first non-null result wins
            var result: ResolvedStream? = null
            try {
                // Round 1: whichever finishes first
                select<Unit> {
                    apiJob.onAwait { r ->
                        if (r != null) {
                            result = r
                            webViewJob.cancel()
                        }
                    }
                    webViewJob.onAwait { r ->
                        if (r != null) {
                            result = r
                            apiJob.cancel()
                        }
                    }
                }
                // If the first to finish returned null, wait for the other
                if (result == null) {
                    result = if (!apiJob.isCancelled) apiJob.await()
                             else if (!webViewJob.isCancelled) webViewJob.await()
                             else null
                }
            } finally {
                if (apiJob.isActive) apiJob.cancel()
                if (webViewJob.isActive) webViewJob.cancel()
            }

            // If primary embed failed and we have activity, try next embed URL in WebView
            if (result == null && activity != null) {
                val fallbackUrl = embedUrls.getOrNull(1)?.second
                if (fallbackUrl != null) {
                    result = StreamExtractor(context).extractStreamUrl(fallbackUrl, activity = activity)
                }
            }

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
            ContentType.MOVIE -> {
                StreamSources.movieSources.forEach { src ->
                    list.add(src.name to src.movieUrl(tmdbId))
                }
            }
            ContentType.TV -> {
                StreamSources.tvSources.forEach { src ->
                    list.add(src.name to src.tvUrl(tmdbId, season, episode))
                }
            }
        }
        return list.toList()
    }
}
