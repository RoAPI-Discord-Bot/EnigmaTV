package com.enigma.tv.data

/**
 * Embed providers — URL patterns aligned with Flux, vidsrc.cc, and common TMDB apps.
 * WebView embed is the reliable tier; native extraction is attempted in parallel.
 *
 * Order matters: first provider is used as the primary embed URL in the WebView,
 * and also the first source tried during native stream extraction.
 * Put the most reliable providers first.
 */
data class StreamSource(
    val name: String,
    val movieUrl: (Int) -> String = { "" },
    val tvUrl: (Int, Int, Int) -> String = { _, _, _ -> "" }
)

object StreamSources {
    val movieSources: List<StreamSource> = listOf(
        // Tier 1: Most reliable — good for new releases, rarely 403
        StreamSource("AutoEmbed",    movieUrl = { id -> "https://autoembed.co/movie/tmdb/$id" }),
        StreamSource("Embed.su",     movieUrl = { id -> "https://embed.su/embed/movie/$id" }),
        StreamSource("VidLink",      movieUrl = { id -> "https://vidlink.pro/movie/$id" }),
        StreamSource("Vidsrc.cc",    movieUrl = { id -> "https://vidsrc.cc/embed/movie/$id" }),
        // Tier 2: Good alternatives
        StreamSource("Vidsrc.xyz",   movieUrl = { id -> "https://vidsrc.xyz/embed/movie?tmdb=$id" }),
        StreamSource("Vidsrc.rip",   movieUrl = { id -> "https://vidsrc.rip/embed/movie/$id" }),
        StreamSource("2Embed",       movieUrl = { id -> "https://www.2embed.skin/embed/movie/$id" }),
        StreamSource("SuperEmbed",   movieUrl = { id -> "https://multiembed.mov/?video_id=$id&tmdb=1" }),
        StreamSource("Moviesapi",    movieUrl = { id -> "https://moviesapi.club/movie/$id" }),
        // Tier 3: Last resort — may be slower or region-restricted
        StreamSource("Vidsrc.to",    movieUrl = { id -> "https://vidsrc.to/embed/movie/$id" }),
        StreamSource("111Movies",    movieUrl = { id -> "https://111movies.com/embed/movie/$id" }),
    )

    val tvSources: List<StreamSource> = listOf(
        // Tier 1
        StreamSource("AutoEmbed TV",  tvUrl = { id, s, e -> "https://autoembed.co/tv/tmdb/$id-$s-$e" }),
        StreamSource("Embed.su TV",   tvUrl = { id, s, e -> "https://embed.su/embed/tv/$id/$s/$e" }),
        StreamSource("VidLink TV",    tvUrl = { id, s, e -> "https://vidlink.pro/tv/$id/$s/$e" }),
        StreamSource("Vidsrc.cc TV",  tvUrl = { id, s, e -> "https://vidsrc.cc/embed/tv/$id/$s/$e" }),
        // Tier 2
        StreamSource("Vidsrc.xyz TV", tvUrl = { id, s, e -> "https://vidsrc.xyz/embed/tv?tmdb=$id&season=$s&episode=$e" }),
        StreamSource("Vidsrc.rip TV", tvUrl = { id, s, e -> "https://vidsrc.rip/embed/tv/$id/$s/$e" }),
        StreamSource("2Embed TV",     tvUrl = { id, s, e -> "https://www.2embed.skin/embed/tv/$id/$s/$e" }),
        StreamSource("SuperEmbed TV", tvUrl = { id, s, e -> "https://multiembed.mov/?video_id=$id&tmdb=1&s=$s&e=$e" }),
        StreamSource("Moviesapi TV",  tvUrl = { id, s, e -> "https://moviesapi.club/tv/$id-$s-$e" }),
        // Tier 3
        StreamSource("Vidsrc.to TV",  tvUrl = { id, s, e -> "https://vidsrc.to/embed/tv/$id/$s/$e" }),
        StreamSource("111Movies TV",  tvUrl = { id, s, e -> "https://111movies.com/embed/tv/$id/$s/$e" }),
    )

    fun movieUrl(sourceIndex: Int, tmdbId: Int): Pair<String, String> {
        val sources = movieSources
        val index = sourceIndex.mod(sources.size)
        val source = sources[index]
        return source.name to source.movieUrl(tmdbId)
    }

    fun tvUrl(sourceIndex: Int, tmdbId: Int, season: Int, episode: Int): Pair<String, String> {
        val sources = tvSources
        val index = sourceIndex.mod(sources.size)
        val source = sources[index]
        return source.name to source.tvUrl(tmdbId, season, episode)
    }
}
