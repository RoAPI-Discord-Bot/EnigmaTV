package com.enigma.tv.data

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates the full playback resolution chain.
 *
 * Resolution is delegated to EmbedProvidersResolver which races:
 *   - VidLink direct API (fast JSON path)
 *   - Hidden WebView extractor on primary embed URL (JS-capable, catches anything)
 *
 * The visible WebViewPlayer is already rendering in the background while this runs,
 * so the user never sees a black screen. This just upgrades them to ExoPlayer
 * (better controls, seek bar, CC) as soon as a native stream is found.
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
    ): ResolvedStream? = withTimeoutOrNull(45_000) {
        resolveInternal(context, embedUrl, activity, tmdbId, type, season, episode)
    }

    private suspend fun resolveInternal(
        context: Context,
        embedUrl: String,
        activity: Activity?,
        tmdbId: Int?,
        type: ContentType?,
        season: Int,
        episode: Int
    ): ResolvedStream? {
        // Direct stream (already an .m3u8 or .mp4) — no extraction needed
        if (isDirectStream(embedUrl)) {
            return ResolvedStream.fromEmbed(embedUrl, embedUrl, "direct")
        }

        val id = tmdbId
        val contentType = type ?: ContentType.MOVIE

        // Race VidLink API vs WebView extractor
        val stream = if (id != null) {
            EmbedProvidersResolver.resolveFromAllProviders(
                context = context,
                activity = activity,
                tmdbId = id,
                type = contentType,
                season = season,
                episode = episode,
                preferredEmbedUrl = embedUrl
            )
        } else {
            // No TMDB ID — just try to extract the provided URL directly
            if (activity != null)
                StreamExtractor(context).extractStreamUrl(embedUrl, activity = activity)
            else null
        } ?: return null

        // Attach subtitles if found, but don't let it stall playback!
        // Give it a strict 4-second timeout. If we don't find it fast, just play the video.
        val subtitleUrl = stream.subtitleUrl ?: kotlinx.coroutines.withTimeoutOrNull(4_000) {
            StreamResolver.resolveSubtitlesForStream(stream.url, embedUrl)
        }
        return stream.copy(subtitleUrl = subtitleUrl)
    }

    private fun isDirectStream(url: String): Boolean {
        if (url.contains(".m3u8", ignoreCase = true)) return true
        return url.contains(".mp4", ignoreCase = true) && !url.contains("embed", ignoreCase = true)
    }
}
