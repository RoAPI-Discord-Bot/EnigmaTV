package com.enigma.tv.data

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tries every configured embed host (Vidsrc, 2Embed, etc.) via HTTP + hidden WebView — mirrors Stremio-style failover.
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
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val urls = buildEmbedList(tmdbId, type, season, episode, preferredEmbedUrl)
        val extractor = StreamExtractor(context)
        for ((name, embedUrl) in urls) {
            StreamResolver.resolveDirectUrl(embedUrl)?.let {
                return@withContext ResolvedStream.fromEmbed(embedUrl, it, name)
            }
        }
        if (activity != null) {
            urls.take(3).forEach { (_, embedUrl) ->
                extractor.extractStreamUrl(embedUrl, activity = activity)?.let { return@withContext it }
            }
        }
        null
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
                list.add("Vidsrc.me" to "https://vidsrc.me/embed/movie?tmdb=$tmdbId")
                list.add("Vidsrc.xyz" to "https://vidsrc.xyz/embed/movie/$tmdbId")
            }
            ContentType.TV -> {
                StreamSources.tvSources.forEach { src ->
                    list.add(src.name to src.tvUrl(tmdbId, season, episode))
                }
                list.add("Vidsrc.me TV" to "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode")
                list.add("Vidsrc.xyz TV" to "https://vidsrc.xyz/embed/tv/$tmdbId/$season/$episode")
            }
        }
        return list.toList()
    }
}
