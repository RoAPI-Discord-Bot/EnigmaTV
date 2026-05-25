package com.enigma.tv.data

import android.app.Activity
import android.content.Context

/**
 * Full playback resolution chain used by movie/TV apps:
 * 1) VidLink API (TMDB)  2) All embed mirrors  3) Current embed HTTP  4) WebView intercept
 */
object StreamPlaybackResolver {

    suspend fun resolve(
        context: Context,
        embedUrl: String,
        activity: Activity?,
        tmdbId: Int?,
        type: ContentType?,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream? {
        if (isDirectStream(embedUrl)) {
            return ResolvedStream.fromEmbed(embedUrl, embedUrl, "direct")
        }

        val id = tmdbId
        val contentType = type ?: ContentType.MOVIE

        if (id != null) {
            when (contentType) {
                ContentType.MOVIE -> VidLinkResolver.resolveMovie(id)?.let { return it }
                ContentType.TV -> VidLinkResolver.resolveTv(id, season, episode)?.let { return it }
            }

            EmbedProvidersResolver.resolveFromAllProviders(
                context = context,
                activity = activity,
                tmdbId = id,
                type = contentType,
                season = season,
                episode = episode,
                preferredEmbedUrl = embedUrl
            )?.let { return it }
        }

        return resolveSingleEmbed(context, embedUrl, activity)
    }

    private suspend fun resolveSingleEmbed(
        context: Context,
        embedUrl: String,
        activity: Activity?
    ): ResolvedStream? {
        StreamResolver.resolveDirectUrl(embedUrl)?.let {
            return ResolvedStream.fromEmbed(embedUrl, it, "scrape")
        }
        if (activity != null) {
            StreamExtractor(context).extractStreamUrl(embedUrl, activity = activity)?.let { return it }
        }
        return null
    }

    private fun isDirectStream(url: String): Boolean {
        if (url.contains(".m3u8", ignoreCase = true)) return true
        return url.contains(".mp4", ignoreCase = true) && !url.contains("embed", ignoreCase = true)
    }
}
