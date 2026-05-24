package com.enigma.tv.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class TmdbPage<T>(
    val page: Int = 1,
    val results: List<T> = emptyList()
)

data class MovieItem(
    val id: Int,
    val title: String,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null
) {
    val year: String get() = releaseDate?.split("-")?.firstOrNull() ?: "?"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }

    fun toFavorite() = FavoriteItem(
        id = id,
        title = title,
        poster = posterUrl ?: "",
        type = ContentType.MOVIE,
        year = year
    )
}

data class TvItem(
    val id: Int,
    val name: String,
    @SerializedName("original_name") val originalName: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null
) {
    val displayName: String get() = name.ifBlank { originalName ?: "Unknown" }
    val year: String get() = firstAirDate?.split("-")?.firstOrNull() ?: "?"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }

    fun toFavorite() = FavoriteItem(
        id = id,
        title = displayName,
        poster = posterUrl ?: "",
        type = ContentType.TV,
        year = year
    )
}

data class TvSeason(
    @SerializedName("season_number") val seasonNumber: Int,
    val name: String? = null
)

data class TvShowDetail(
    val id: Int,
    val name: String,
    @SerializedName("poster_path") val posterPath: String? = null,
    val seasons: List<TvSeason> = emptyList()
)

data class TvEpisode(
    @SerializedName("episode_number") val episodeNumber: Int,
    val name: String
)

data class TvSeasonDetail(
    val episodes: List<TvEpisode> = emptyList()
)

data class ContinueWatchingEntry(
    val id: Int,
    val name: String,
    val poster: String = "",
    val season: Int = 1,
    val episode: Int = 1
)

enum class ContentType { MOVIE, TV }

data class FavoriteItem(
    val id: Int,
    val title: String,
    val poster: String = "",
    val type: ContentType,
    val year: String = "?"
)

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<FavoriteItem> = emptyList()
)

data class SearchResults(
    val movies: List<MovieItem> = emptyList(),
    val tv: List<TvItem> = emptyList()
)
