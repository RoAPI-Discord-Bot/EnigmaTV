package com.enigma.tv.data

data class StreamSource(
    val name: String,
    val movieUrl: (Int) -> String = { "" },
    val tvUrl: (Int, Int, Int) -> String = { _, _, _ -> "" }
)

object StreamSources {
    /** First 3 are the reliable set from testing; alternates replace the broken embeds. */
    val movieSources: List<StreamSource> = listOf(
        StreamSource("VidLink (Best UI)", movieUrl = { id -> "https://vidlink.pro/movie/$id" }),
        StreamSource("Vidsrc.to (Reliable)", movieUrl = { id -> "https://vidsrc.to/embed/movie/$id" }),
        StreamSource("Vsembed.ru (Original)", movieUrl = { id -> "https://vsembed.ru/embed/movie/$id" }),
        StreamSource("Vidsrc.cc", movieUrl = { id -> "https://vidsrc.cc/v2/embed/movie/$id" }),
        StreamSource("Vidsrc.me", movieUrl = { id -> "https://vidsrc.me/embed/movie?tmdb=$id" }),
        StreamSource("Embed.su", movieUrl = { id -> "https://embed.su/embed/movie/$id" })
    )

    val tvSources: List<StreamSource> = listOf(
        StreamSource("VidLink TV", tvUrl = { id, s, e -> "https://vidlink.pro/tv/$id/$s/$e" }),
        StreamSource("2Embed TV", tvUrl = { id, s, e -> "https://www.2embed.skin/embed/tv/$id/$s/$e" }),
        StreamSource("Vidsrc.to TV", tvUrl = { id, s, e -> "https://vidsrc.to/embed/tv/$id/$s/$e" }),
        StreamSource("Vidsrc.cc TV", tvUrl = { id, s, e -> "https://vidsrc.cc/v2/embed/tv/$id/$s/$e" }),
        StreamSource("Vidsrc.me TV", tvUrl = { id, s, e ->
            "https://vidsrc.me/embed/tv?tmdb=$id&season=$s&episode=$e"
        }),
        StreamSource("Embed.su TV", tvUrl = { id, s, e -> "https://embed.su/embed/tv/$id/$s/$e" }),
        StreamSource("MultiEmbed TV", tvUrl = { id, s, e ->
            "https://multiembed.mov/?video_id=$id&tmdb=1&s=$s&e=$e"
        })
    )

    fun movieUrl(sourceIndex: Int, tmdbId: Int): Pair<String, String> {
        val sources = movieSources
        val index = sourceIndex % sources.size
        val source = sources[index]
        return source.name to source.movieUrl(tmdbId)
    }

    fun tvUrl(sourceIndex: Int, tmdbId: Int, season: Int, episode: Int): Pair<String, String> {
        val sources = tvSources
        val index = sourceIndex % sources.size
        val source = sources[index]
        return source.name to source.tvUrl(tmdbId, season, episode)
    }
}
