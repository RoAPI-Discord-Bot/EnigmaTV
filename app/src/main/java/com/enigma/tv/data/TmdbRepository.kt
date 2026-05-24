package com.enigma.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate

object TmdbConfig {
    const val API_KEY = "2dca580c2a14b55200e784d157207b4d"
    const val BASE_URL = "https://api.themoviedb.org/3/"
}

class TmdbRepository {
    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl(TmdbConfig.BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    private val key get() = TmdbConfig.API_KEY
    private val today get() = LocalDate.now().toString()

    suspend fun trendingMovies() = api.trendingMovies(key).results
    suspend fun popularMovies() = api.popularMovies(key).results
    suspend fun nowPlayingMovies() = api.nowPlayingMovies(key).results
    suspend fun upcomingMovies() = api.upcomingMovies(key).results
    suspend fun topRatedMovies() = api.topRatedMovies(key).results
    suspend fun recentlyAddedMovies() = api.discoverMovies(key, sortBy = "release_date.desc").results.take(20)
    suspend fun searchMovies(query: String) = api.searchMovies(key, query).results
    suspend fun movieDetail(id: Int) = api.movieDetail(id, key)

    suspend fun trendingTv() = api.trendingTv(key).results
    suspend fun popularTv() = api.popularTv(key).results
    suspend fun onTheAirTv() = api.onTheAirTv(key).results
    suspend fun topRatedTv() = api.topRatedTv(key).results
    suspend fun airingTodayTv() = api.airingTodayTv(key).results
    suspend fun searchTv(query: String) = api.searchTv(key, query).results
    suspend fun tvDetail(id: Int) = api.tvDetail(id, key)
    suspend fun tvSeason(id: Int, season: Int) = api.tvSeason(id, season, key).episodes

    suspend fun buildHomeRows(): List<HomeRow> = coroutineScope {
        val inTheaters = async { nowPlayingMovies().take(15) }
        val upcoming = async { upcomingMovies().take(15) }
        val topMovies = async { topRatedMovies().take(15) }
        val trendMovies = async { trendingMovies().take(15) }
        val popMovies = async { popularMovies().take(15) }
        val recentMovies = async { recentlyAddedMovies() }
        val trendTv = async { trendingTv().take(15) }
        val popTv = async { popularTv().take(15) }
        val onAir = async { onTheAirTv().take(15) }
        val topTv = async { topRatedTv().take(15) }
        val airingToday = async { airingTodayTv().take(15) }

        listOf(
            HomeRow.Movies("🎬 In Theaters", inTheaters.await()),
            HomeRow.Movies("🆕 Upcoming", upcoming.await()),
            HomeRow.Movies("⭐ Top Rated Movies", topMovies.await()),
            HomeRow.Movies("🔥 Trending Movies", trendMovies.await()),
            HomeRow.Movies("🌟 Popular Movies", popMovies.await()),
            HomeRow.Movies("📥 Recently Added", recentMovies.await()),
            HomeRow.TvShows("📺 Trending TV", trendTv.await()),
            HomeRow.TvShows("🌟 Popular TV", popTv.await()),
            HomeRow.TvShows("📡 On The Air", onAir.await()),
            HomeRow.TvShows("⭐ Top Rated TV", topTv.await()),
            HomeRow.TvShows("📅 Airing Today", airingToday.await())
        )
    }.filter { row ->
        when (row) {
            is HomeRow.Movies -> row.items.isNotEmpty()
            is HomeRow.TvShows -> row.items.isNotEmpty()
        }
    }
}
