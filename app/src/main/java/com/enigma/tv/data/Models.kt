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

data class VideoResults(
    val results: List<MediaVideo> = emptyList()
)

data class MediaVideo(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false
) {
    val youtubeWatchUrl: String?
        get() = if (site.equals("YouTube", ignoreCase = true) && key.isNotBlank()) {
            "https://www.youtube.com/watch?v=$key"
        } else null

    val youtubeThumbnailUrl: String?
        get() = if (key.isNotBlank()) "https://img.youtube.com/vi/$key/hqdefault.jpg" else null

    val isTrailerLike: Boolean
        get() = type.equals("Trailer", ignoreCase = true) ||
            type.equals("Teaser", ignoreCase = true) ||
            type.equals("Clip", ignoreCase = true)
}

fun VideoResults?.pickTrailers(): List<MediaVideo> =
    this?.results
        ?.filter { it.youtubeWatchUrl != null && it.isTrailerLike }
        ?.sortedWith(compareByDescending<MediaVideo> { it.official }.thenBy { it.type != "Trailer" })
        ?: emptyList()

data class MediaTrailerUi(
    val name: String,
    val youtubeUrl: String,
    val thumbnailUrl: String?,
    val official: Boolean
)

data class MovieReleaseDates(val results: List<ReleaseDateCountry> = emptyList())

data class ReleaseDateCountry(
    @SerializedName("iso_3166_1") val iso31661: String,
    @SerializedName("release_dates") val releaseDates: List<ReleaseDateEntry> = emptyList()
)

data class ReleaseDateEntry(
    val certification: String? = null,
    val type: Int = 0
)

data class TvContentRatings(val results: List<TvContentRatingEntry> = emptyList())

data class TvContentRatingEntry(
    @SerializedName("iso_3166_1") val iso31661: String,
    val rating: String = ""
)

/** US MPAA / TV parental guidance label (PG-13, R, TV-14, TV-MA, …). */
fun MovieReleaseDates?.usContentRating(): String? {
    val us = this?.results?.find { it.iso31661.equals("US", ignoreCase = true) } ?: return null
    val rated = us.releaseDates.mapNotNull { entry ->
        entry.certification?.trim()?.takeIf { it.isNotEmpty() }
    }
    if (rated.isEmpty()) return null
    val theatrical = us.releaseDates.find { it.type == 3 }?.certification?.trim()?.takeIf { it.isNotEmpty() }
    return theatrical ?: rated.first()
}

fun TvContentRatings?.usContentRating(): String? =
    this?.results
        ?.find { it.iso31661.equals("US", ignoreCase = true) }
        ?.rating
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

data class ExternalIds(
    @SerializedName("imdb_id") val imdbId: String? = null
)

data class MovieDetailResponse(
    val id: Int,
    val title: String,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    val runtime: Int? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    val videos: VideoResults? = null,
    @SerializedName("release_dates") val releaseDates: MovieReleaseDates? = null,
    @SerializedName("external_ids") val externalIds: ExternalIds? = null
)

data class TvShowDetail(
    val id: Int,
    val name: String,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    val seasons: List<TvSeason> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    val videos: VideoResults? = null,
    @SerializedName("content_ratings") val contentRatings: TvContentRatings? = null,
    @SerializedName("external_ids") val externalIds: ExternalIds? = null
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
    /** Exact playback position in milliseconds (resume here in Exo). */
    val positionMs: Long = 0,
    /** Total content duration in milliseconds — used for progress bar + time remaining display. */
    val durationMs: Long = 0,
    /** Legacy field — ignored when [positionMs] > 0 */
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

data class SearchSuggestion(
    val id: Int,
    val title: String,
    val type: ContentType,
    val year: String = "?"
)

data class MultiSearchItem(
    val id: Int = 0,
    @SerializedName("media_type") val mediaType: String = "",
    val title: String? = null,
    val name: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null
) {
    val displayTitle: String get() = (title ?: name).orEmpty()
}

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
    val ratingScore: String,
    val ratingVotes: String?,
    val imdbRating: String? = null,
    val rottenTomatoesRating: String? = null,
    /** US content rating: PG, PG-13, R, TV-PG, TV-14, TV-MA, etc. */
    val contentRating: String? = null,
    val genresText: String,
    val cast: List<CastMember>,
    val trailers: List<MediaTrailerUi> = emptyList(),
    val isPlayable: Boolean,
    val seasons: List<Int> = emptyList(),
    val episodes: List<TvEpisode> = emptyList(),
    val selectedSeason: Int = 1,
    val selectedEpisode: Int = 1,
    val isFavorite: Boolean = false,
    val resumePositionMs: Long = 0L,
    val resumeSeason: Int? = null,
    val resumeEpisode: Int? = null,
    /** True when this item exists in the user's Continue Watching list, even if positionMs == 0 */
    val isInContinueWatching: Boolean = false
)
