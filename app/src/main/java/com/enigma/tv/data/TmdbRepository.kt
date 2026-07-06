package com.enigma.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
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
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    private val omdbApi: OmdbApi = Retrofit.Builder()
        .baseUrl("http://www.omdbapi.com/")
        .client(
            OkHttpClient.Builder()
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OmdbApi::class.java)

    private val key get() = TmdbConfig.API_KEY

    private suspend fun <T> safe(block: suspend () -> T, fallback: T): T =
        runCatching { block() }.getOrElse { fallback }

    suspend fun trendingMovies() = api.trendingMovies(key).results
    suspend fun popularMovies() = api.popularMovies(key).results
    suspend fun nowPlayingMovies() = api.nowPlayingMovies(key).results
    suspend fun upcomingMovies() = api.upcomingMovies(key).results
    suspend fun topRatedMovies() = api.topRatedMovies(key).results
    suspend fun recentlyAddedMovies(): List<MovieItem> = safe(
        { api.discoverMovies(key, sortBy = "release_date.desc").results.take(20) },
        popularMovies().take(20)
    )
    suspend fun searchMovies(query: String) = api.searchMovies(key, query).results

    suspend fun searchSuggestions(query: String): List<SearchSuggestion> {
        if (query.trim().length < 2) return emptyList()
        return runCatching {
            api.searchMulti(key, query.trim())
                .results
                .mapNotNull { item ->
                    when (item.mediaType) {
                        "movie" -> SearchSuggestion(
                            id = item.id,
                            title = item.displayTitle,
                            type = ContentType.MOVIE,
                            year = item.releaseDate?.split("-")?.firstOrNull() ?: "?"
                        )
                        "tv" -> SearchSuggestion(
                            id = item.id,
                            title = item.displayTitle,
                            type = ContentType.TV,
                            year = item.firstAirDate?.split("-")?.firstOrNull() ?: "?"
                        )
                        else -> null
                    }
                }
                .take(8)
        }.getOrElse { emptyList() }
    }

    suspend fun omdbRatings(imdbId: String): OmdbResponse? = safe({ omdbApi.getRatings(imdbId) }, null)

    suspend fun movieDetail(id: Int) = api.movieDetail(id, key)

    suspend fun trendingTv() = api.trendingTv(key).results
    suspend fun popularTv() = api.popularTv(key).results
    suspend fun onTheAirTv() = api.onTheAirTv(key).results
    suspend fun topRatedTv() = api.topRatedTv(key).results
    suspend fun airingTodayTv() = api.airingTodayTv(key).results
    suspend fun searchTv(query: String) = api.searchTv(key, query).results
    suspend fun tvDetail(id: Int) = api.tvDetail(id, key)
    suspend fun tvSeason(id: Int, season: Int) = api.tvSeason(id, season, key).episodes

    suspend fun discoverMoviesByGenre(genreId: Int) = safe(
        { api.discoverMoviesByGenre(key, genreId).results.take(15) },
        emptyList<MovieItem>()
    )
    suspend fun discoverTvByGenre(genreId: Int) = safe(
        { api.discoverTvByGenre(key, genreId).results.take(15) },
        emptyList<TvItem>()
    )

    // TMDB Genre IDs reference
    object Genre {
        const val ACTION = 28; const val ACTION_TV = 10759
        const val SCIFI = 878; const val SCIFI_TV = 10765
        const val COMEDY = 35
        const val HORROR = 27
        const val DRAMA = 18
        const val ANIMATION = 16
        const val DOCUMENTARY = 99
        const val THRILLER = 53
    }

    suspend fun buildHomeRows(): List<HomeRow> = supervisorScope {
        val inTheaters = async { safe({ nowPlayingMovies().take(15) }, emptyList<MovieItem>()) }
        val upcoming = async { safe({ upcomingMovies().take(15) }, emptyList<MovieItem>()) }
        val topMovies = async { safe({ topRatedMovies().take(15) }, emptyList<MovieItem>()) }
        val trendMovies = async { safe({ trendingMovies().take(15) }, emptyList<MovieItem>()) }
        val trendTv = async { safe({ trendingTv().take(15) }, emptyList<TvItem>()) }
        val popTv = async { safe({ popularTv().take(15) }, emptyList<TvItem>()) }
        val onAir = async { safe({ onTheAirTv().take(15) }, emptyList<TvItem>()) }
        val topTv = async { safe({ topRatedTv().take(15) }, emptyList<TvItem>()) }
        val airingToday = async { safe({ airingTodayTv().take(15) }, emptyList<TvItem>()) }
        // Smart genre collections
        val actionMovies = async { discoverMoviesByGenre(Genre.ACTION) }
        val scifiMovies = async { discoverMoviesByGenre(Genre.SCIFI) }
        val horrorMovies = async { discoverMoviesByGenre(Genre.HORROR) }
        val comedyMovies = async { discoverMoviesByGenre(Genre.COMEDY) }
        val actionTv = async { discoverTvByGenre(Genre.ACTION_TV) }
        val scifiTv = async { discoverTvByGenre(Genre.SCIFI_TV) }
        val animeTv = async { discoverTvByGenre(Genre.ANIMATION) }

        listOf(
            HomeRow.Movies("🎬 In Theaters", inTheaters.await()),
            HomeRow.Movies("🆕 Upcoming", upcoming.await()),
            HomeRow.TvShows("📺 Trending TV", trendTv.await()),
            HomeRow.Movies("🔥 Trending Movies", trendMovies.await()),
            HomeRow.TvShows("📡 On The Air", onAir.await()),
            HomeRow.Movies("💥 Action & Adventure", actionMovies.await()),
            HomeRow.TvShows("⚔️ Action TV", actionTv.await()),
            HomeRow.Movies("🚀 Sci-Fi", scifiMovies.await()),
            HomeRow.TvShows("🌌 Sci-Fi & Fantasy TV", scifiTv.await()),
            HomeRow.Movies("😱 Horror", horrorMovies.await()),
            HomeRow.Movies("😂 Comedy", comedyMovies.await()),
            HomeRow.TvShows("🎌 Animation", animeTv.await()),
            HomeRow.TvShows("🌟 Popular TV", popTv.await()),
            HomeRow.Movies("⭐ Top Rated Movies", topMovies.await()),
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
