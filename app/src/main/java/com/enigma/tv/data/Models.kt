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
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null
) {
    val year: String get() = releaseDate?.split("-")?.firstOrNull() ?: "?"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }

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
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null
) {
    val displayName: String get() = name.ifBlank { originalName ?: "Unknown" }
    val year: String get() = firstAirDate?.split("-")?.firstOrNull() ?: "?"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }

    fun toFavorite() = FavoriteItem(
        id = id,
        title = displayName,
        poster = posterUrl ?: "",
        type = ContentType.TV,
        year = year
    )
}

data class Genre(@SerializedName("name") val name: String = "")
data class CastMember(val id: Int, val name: String, val character: String?, @SerializedName("profile_path") val profilePath: String?) {
    val photoUrl: String? get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

data class Credits(@SerializedName("cast") val cast: List<CastMember> = emptyList())

data class MovieDetailResponse(
    val id: Int,
    val title: String,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    val runtime: Int? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null
)

data class TvShowDetail(
    val id: Int,
    val name: String,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    val seasons: List<TvSeason> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0
)

data class TvSeason(
    @SerializedName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerializedName("episode_count") val episodeCount: Int = 0
)

data class TvEpisode(
    @SerializedName("episode_number") val episodeNumber: Int,
    val name: String,
    val overview: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("runtime") val runtime: Int? = null
) {
    val stillUrl: String? get() = stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

data class TvSeasonDetail(val episodes: List<TvEpisode> = emptyList())

enum class ContentType { MOVIE, TV }

data class ContinueWatchingEntry(
    val id: Int,
    val name: String,
    val poster: String = "",
    val season: Int = 1,
    val episode: Int = 1,
    val type: ContentType = ContentType.TV,
    /** 0–100 for movies; TV uses season/episode */
    val progressPercent: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

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

sealed class HomeRow {
    abstract val title: String
    data class Movies(override val title: String, val items: List<MovieItem>) : HomeRow()
    data class TvShows(override val title: String, val items: List<TvItem>) : HomeRow()
}

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = ""
)

data class MediaDetailUi(
    val type: ContentType,
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val metaLine: String,
    val releaseLabel: String?,
    val ratingText: String,
    val genresText: String,
    val cast: List<CastMember>,
    val isPlayable: Boolean,
    val seasons: List<Int> = emptyList(),
    val episodes: List<TvEpisode> = emptyList(),
    val selectedSeason: Int = 1,
    val selectedEpisode: Int = 1,
    val isFavorite: Boolean = false
)
