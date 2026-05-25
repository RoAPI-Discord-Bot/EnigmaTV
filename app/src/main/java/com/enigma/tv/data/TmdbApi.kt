package com.enigma.tv.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week")
    suspend fun trendingMovies(@Query("api_key") apiKey: String): TmdbPage<MovieItem>

    @GET("movie/popular")
    suspend fun popularMovies(@Query("api_key") apiKey: String): TmdbPage<MovieItem>

    @GET("movie/now_playing")
    suspend fun nowPlayingMovies(@Query("api_key") apiKey: String): TmdbPage<MovieItem>

    @GET("movie/upcoming")
    suspend fun upcomingMovies(@Query("api_key") apiKey: String): TmdbPage<MovieItem>

    @GET("movie/top_rated")
    suspend fun topRatedMovies(@Query("api_key") apiKey: String): TmdbPage<MovieItem>

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "release_date.desc",
        @Query("primary_release_date.lte") maxDate: String? = null
    ): TmdbPage<MovieItem>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String
    ): TmdbPage<MovieItem>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPage<MultiSearchItem>

    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "credits,videos,release_dates"
    ): MovieDetailResponse

    @GET("trending/tv/week")
    suspend fun trendingTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("tv/popular")
    suspend fun popularTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("tv/on_the_air")
    suspend fun onTheAirTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("tv/top_rated")
    suspend fun topRatedTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("tv/airing_today")
    suspend fun airingTodayTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String
    ): TmdbPage<TvItem>

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "credits,videos,content_ratings"
    ): TvShowDetail

    @GET("tv/{id}/season/{season}")
    suspend fun tvSeason(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("api_key") apiKey: String
    ): TvSeasonDetail
}
