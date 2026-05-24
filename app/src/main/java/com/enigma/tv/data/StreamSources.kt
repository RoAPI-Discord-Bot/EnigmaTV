package com.enigma.tv.data

data class StreamSource(
    val name: String,
    val movieUrl: (Int) -> String = { "" },
    val tvUrl: (Int, Int, Int) -> String = { _, _, _ -> "" }
)

object StreamSources {
    val movieSources: List<StreamSource> = listOf(
        StreamSource("VidLink (Best UI)") { id -> "https://vidlink.pro/movie/$id" },
        StreamSource("Vidsrc.to (Reliable)") { id -> "https://vidsrc.to/embed/movie/$id" },
        StreamSource("Vsembed.ru (Original)") { id -> "https://vsembed.ru/embed/movie/$id" },
        StreamSource("AutoEmbed (Aggregator)") { id -> "https://player.autoembed.cc/embed/movie/$id" },
        StreamSource("SuperEmbed") { id -> "https://multiembed.mov/?video_id=$id&tmdb=1" },
        StreamSource("2Embed") { id -> "https://www.2embed.cc/embed/$id" }
    )

    val tvSources: List<StreamSource> = listOf(
        StreamSource("VidLink TV", tvUrl = { id, s, e -> "https://vidlink.pro/tv/$id/$s/$e" }),
        StreamSource("Vidsrc.to TV", tvUrl = { id, s, e -> "https://vidsrc.to/embed/tv/$id/$s/$e" }),
        StreamSource("AutoEmbed TV", tvUrl = { id, s, e -> "https://player.autoembed.cc/embed/tv/$id/$s/$e" }),
        StreamSource("2Embed TV", tvUrl = { id, s, e -> "https://www.2embed.cc/embedtv/$id&s=$s&e=$e" }),
        StreamSource("SmashyStream", tvUrl = { id, s, e ->
            "https://embed.smashystream.com/playere.php?tmdb=$id&season=$s&episode=$e"
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
