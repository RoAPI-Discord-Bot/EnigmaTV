package com.enigma.tv.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week")
    suspend fun trendingMovies(@Query("api_key") apiKey: String): TmdbPage<MovieItem>

    @GET("movie/popular")
    suspend fun popularMovies(@Query("api_key") apiKey: String): TmdbPage<MovieItem>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String
    ): TmdbPage<MovieItem>

    @GET("trending/tv/week")
    suspend fun trendingTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("tv/popular")
    suspend fun popularTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("tv/on_the_air")
    suspend fun onTheAirTv(@Query("api_key") apiKey: String): TmdbPage<TvItem>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String
    ): TmdbPage<TvItem>

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): TvShowDetail

    @GET("tv/{id}/season/{season}")
    suspend fun tvSeason(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("api_key") apiKey: String
    ): TvSeasonDetail
}
