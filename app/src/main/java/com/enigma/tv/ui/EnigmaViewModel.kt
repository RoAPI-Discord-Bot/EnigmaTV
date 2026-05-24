package com.enigma.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.ContinueWatchingStore
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.FavoritesStore
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.Playlist
import com.enigma.tv.data.PlaylistStore
import com.enigma.tv.data.SearchResults
import com.enigma.tv.data.StreamSources
import com.enigma.tv.data.TmdbRepository
import com.enigma.tv.data.TvItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NavSection(val title: String) {
    HOME("Home"),
    FAVORITES("Favorites"),
    CONTINUE("Continue Watching"),
    LISTS("My Lists")
}

data class EnigmaUiState(
    val showSplash: Boolean = true,
    val contentLoading: Boolean = true,
    val section: NavSection = NavSection.HOME,
    val trendingMovies: List<MovieItem> = emptyList(),
    val popularMovies: List<MovieItem> = emptyList(),
    val trendingTv: List<TvItem> = emptyList(),
    val popularTv: List<TvItem> = emptyList(),
    val onTheAirTv: List<TvItem> = emptyList(),
    val continueWatching: List<ContinueWatchingEntry> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchResults: SearchResults? = null,
    val error: String? = null,
    val playerVisible: Boolean = false,
    val playerLoading: Boolean = false,
    val playerTitle: String = "",
    val playerUrl: String = "",
    val playerAccentMovie: Boolean = true,
    val sourceIndex: Int = 0,
    val sourceLabel: String = "",
    val playingType: ContentType? = null,
    val currentMovieId: Int? = null,
    val currentShowId: Int? = null,
    val seasons: List<Int> = emptyList(),
    val episodes: List<Pair<Int, String>> = emptyList(),
    val selectedSeason: Int = 1,
    val selectedEpisode: Int = 1,
    val selectedPlaylistId: String? = null
)

class EnigmaViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TmdbRepository()
    private val cwStore = ContinueWatchingStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val playlistStore = PlaylistStore(application)

    private val _state = MutableStateFlow(EnigmaUiState())
    val state: StateFlow<EnigmaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                favoritesStore.favorites,
                playlistStore.playlists,
                cwStore.entries
            ) { favs, lists, cw ->
                Triple(favs, lists, cw)
            }.collect { (favs, lists, cw) ->
                _state.update {
                    it.copy(
                        favorites = favs,
                        playlists = lists,
                        continueWatching = cw
                    )
                }
            }
        }
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            val splashStart = System.currentTimeMillis()
            _state.update {
                it.copy(contentLoading = true, error = null, searchResults = null, section = NavSection.HOME)
            }
            try {
                coroutineScope {
                    val movies = async {
                        val t = repo.trendingMovies()
                        val p = repo.popularMovies()
                        t to p
                    }
                    val tv = async {
                        val t = repo.trendingTv()
                        val p = repo.popularTv()
                        val a = repo.onTheAirTv()
                        Triple(t, p, a)
                    }
                    val (trendM, popM) = movies.await()
                    val (trendT, popT, airT) = tv.await()
                    _state.update {
                        it.copy(
                            trendingMovies = trendM,
                            popularMovies = popM,
                            trendingTv = trendT,
                            popularTv = popT,
                            onTheAirTv = airT
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update { it.copy(error = "Could not load EnigmaTV content") }
            }
            val elapsed = System.currentTimeMillis() - splashStart
            if (elapsed < 1200) delay(1200 - elapsed)
            _state.update { it.copy(contentLoading = false, showSplash = false) }
        }
    }

    fun setSection(section: NavSection) {
        _state.update { it.copy(section = section, searchResults = null, error = null) }
    }

    fun selectPlaylist(playlistId: String?) {
        _state.update { it.copy(selectedPlaylistId = playlistId, section = NavSection.LISTS) }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(contentLoading = true, error = null) }
            try {
                coroutineScope {
                    val movies = async { repo.searchMovies(query) }
                    val tv = async { repo.searchTv(query) }
                    _state.update {
                        it.copy(
                            contentLoading = false,
                            searchResults = SearchResults(movies.await(), tv.await()),
                            section = NavSection.HOME
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update { it.copy(contentLoading = false, error = "Search failed") }
            }
        }
    }

    fun toggleFavorite(item: FavoriteItem) {
        viewModelScope.launch { favoritesStore.toggle(item) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistStore.createPlaylist(name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            playlistStore.deletePlaylist(id)
            if (_state.value.selectedPlaylistId == id) {
                _state.update { it.copy(selectedPlaylistId = null) }
            }
        }
    }

    fun addToPlaylist(playlistId: String, item: FavoriteItem) {
        viewModelScope.launch { playlistStore.addItem(playlistId, item) }
    }

    fun playMovie(movie: MovieItem) {
        val (name, url) = StreamSources.movieUrl(0, movie.id)
        _state.update {
            it.copy(
                playerVisible = true,
                playerLoading = true,
                playerTitle = movie.title,
                playerUrl = url,
                playerAccentMovie = true,
                playingType = ContentType.MOVIE,
                currentMovieId = movie.id,
                currentShowId = null,
                sourceIndex = 0,
                sourceLabel = "$name (1/${StreamSources.movieSources.size})"
            )
        }
        viewModelScope.launch {
            cwStore.addOrUpdate(
                ContinueWatchingEntry(
                    id = movie.id,
                    name = movie.title,
                    poster = movie.posterUrl ?: "",
                    season = 0,
                    episode = 0,
                    type = ContentType.MOVIE
                )
            )
        }
    }

    fun resumeContinue(entry: ContinueWatchingEntry) {
        when (entry.type) {
            ContentType.MOVIE -> playMovie(MovieItem(id = entry.id, title = entry.name))
            ContentType.TV -> selectShow(entry.id, entry.name, entry.season, entry.episode)
        }
    }

    fun playFavorite(item: FavoriteItem) {
        when (item.type) {
            ContentType.MOVIE -> playMovie(
                MovieItem(item.id, item.title, posterPath = null, releaseDate = "${item.year}-01-01")
            )
            ContentType.TV -> selectShow(item.id, item.title)
        }
    }

    fun selectShow(id: Int, name: String, resumeSeason: Int? = null, resumeEpisode: Int? = null) {
        viewModelScope.launch {
            val startSeason = resumeSeason ?: 1
            val startEpisode = resumeEpisode ?: 1
            _state.update {
                it.copy(
                    playerVisible = true,
                    playerLoading = true,
                    playerTitle = name,
                    playerAccentMovie = false,
                    playingType = ContentType.TV,
                    currentShowId = id,
                    currentMovieId = null,
                    sourceIndex = 0,
                    selectedSeason = startSeason,
                    selectedEpisode = startEpisode
                )
            }
            try {
                val detail = repo.tvDetail(id)
                val seasons = detail.seasons.filter { it.seasonNumber > 0 }.map { it.seasonNumber }
                val poster = detail.posterPath?.let { p -> "https://image.tmdb.org/t/p/w200$p" } ?: ""
                cwStore.addOrUpdate(
                    ContinueWatchingEntry(
                        id = id,
                        name = name,
                        poster = poster,
                        season = startSeason,
                        episode = startEpisode,
                        type = ContentType.TV
                    )
                )
                if (seasons.isNotEmpty()) {
                    val season = if (startSeason in seasons) startSeason else seasons.first()
                    loadEpisodes(id, season, startEpisode)
                    _state.update { it.copy(seasons = seasons, selectedSeason = season) }
                } else {
                    playCurrentEpisode()
                }
            } catch (_: Exception) {
                playCurrentEpisode()
            }
        }
    }

    fun loadEpisodes(showId: Int, season: Int, resumeEpisode: Int? = null) {
        viewModelScope.launch {
            _state.update { it.copy(selectedSeason = season) }
            try {
                val eps = repo.tvSeason(showId, season)
                val episodeList = eps.map { it.episodeNumber to it.name }
                val episode = resumeEpisode?.takeIf { n ->
                    episodeList.any { it.first == n }
                } ?: episodeList.firstOrNull()?.first ?: 1
                _state.update { it.copy(episodes = episodeList, selectedEpisode = episode) }
                playCurrentEpisode()
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        episodes = listOf((resumeEpisode ?: 1) to "Episode ${resumeEpisode ?: 1}"),
                        selectedEpisode = resumeEpisode ?: 1
                    )
                }
                playCurrentEpisode()
            }
        }
    }

    fun onSeasonChange(season: Int) {
        val showId = _state.value.currentShowId ?: return
        loadEpisodes(showId, season)
    }

    fun onEpisodeChange(episode: Int) {
        _state.update { it.copy(selectedEpisode = episode) }
        playCurrentEpisode()
    }

    fun playCurrentEpisode() {
        val s = _state.value
        val showId = s.currentShowId ?: return
        val (name, url) = StreamSources.tvUrl(s.sourceIndex, showId, s.selectedSeason, s.selectedEpisode)
        _state.update {
            it.copy(
                playerLoading = true,
                playerUrl = url,
                sourceLabel = "$name (${(s.sourceIndex % StreamSources.tvSources.size) + 1}/${StreamSources.tvSources.size})"
            )
        }
        viewModelScope.launch {
            cwStore.updateProgress(showId, ContentType.TV, s.selectedSeason, s.selectedEpisode)
        }
    }

    fun nextSource() {
        val s = _state.value
        when (s.playingType) {
            ContentType.MOVIE -> {
                val id = s.currentMovieId ?: return
                val next = (s.sourceIndex + 1) % StreamSources.movieSources.size
                val (name, url) = StreamSources.movieUrl(next, id)
                _state.update {
                    it.copy(
                        playerLoading = true,
                        sourceIndex = next,
                        playerUrl = url,
                        sourceLabel = "$name (${next + 1}/${StreamSources.movieSources.size})"
                    )
                }
            }
            ContentType.TV -> {
                _state.update { it.copy(sourceIndex = it.sourceIndex + 1) }
                playCurrentEpisode()
            }
            null -> Unit
        }
    }

    fun onPlayerPageLoading(loading: Boolean) {
        _state.update { it.copy(playerLoading = loading) }
    }

    fun closePlayer() {
        _state.update {
            it.copy(
                playerVisible = false,
                playerUrl = "",
                playerLoading = false,
                playingType = null
            )
        }
    }

}
