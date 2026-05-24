package com.enigma.tv.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TmdbConfig {
    const val API_KEY = "2dca580c2a14b55200e784d157207b4d"
    const val BASE_URL = "https://api.themoviedb.org/3/"
}

class TmdbRepository {
    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl(TmdbConfig.BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    private val key get() = TmdbConfig.API_KEY

    suspend fun trendingMovies() = api.trendingMovies(key).results
    suspend fun popularMovies() = api.popularMovies(key).results
    suspend fun searchMovies(query: String) = api.searchMovies(key, query).results

    suspend fun trendingTv() = api.trendingTv(key).results
    suspend fun popularTv() = api.popularTv(key).results
    suspend fun onTheAirTv() = api.onTheAirTv(key).results
    suspend fun searchTv(query: String) = api.searchTv(key, query).results
    suspend fun tvDetail(id: Int) = api.tvDetail(id, key)
    suspend fun tvSeason(id: Int, season: Int) = api.tvSeason(id, season, key).episodes
}
