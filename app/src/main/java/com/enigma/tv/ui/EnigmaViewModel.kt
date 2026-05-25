package com.enigma.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.ContinueWatchingStore
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.HomeRow
import com.enigma.tv.data.LiveChannel
import com.enigma.tv.data.LiveChannels
import com.enigma.tv.data.MediaDetailUi
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.Playlist
import com.enigma.tv.data.PlaylistStore
import com.enigma.tv.data.SearchResults
import com.enigma.tv.data.StreamSources
import com.enigma.tv.data.TmdbRepository
import com.enigma.tv.data.TvItem
import com.enigma.tv.data.comingSoonLabel
import com.enigma.tv.data.firebase.FirebaseAuthService
import com.enigma.tv.data.firebase.FirebaseSyncService
import com.enigma.tv.data.formatRuntime
import com.enigma.tv.data.isReleased
import com.enigma.tv.data.FavoritesStore
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
    LIVE("Live TV"),
    FAVORITES("Favorites"),
    CONTINUE("Continue Watching"),
    LISTS("My Lists"),
    PROFILE("Account")
}

data class EnigmaUiState(
    val showSplash: Boolean = true,
    val contentLoading: Boolean = true,
    val section: NavSection = NavSection.HOME,
    val homeRows: List<HomeRow> = emptyList(),
    val continueWatching: List<ContinueWatchingEntry> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val liveChannels: List<LiveChannel> = LiveChannels.channels,
    val searchResults: SearchResults? = null,
    val error: String? = null,
    val showDetail: Boolean = false,
    val detailLoading: Boolean = false,
    val detail: MediaDetailUi? = null,
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
    val selectedPlaylistId: String? = null,
    val isLoggedIn: Boolean = false,
    val userEmail: String = "",
    val userDisplayName: String = "",
    val profileMessage: String? = null,
    val profileError: String? = null,
    val selectedSeason: Int = 1,
    val selectedEpisode: Int = 1,
    val seasons: List<Int> = emptyList(),
    val episodes: List<Pair<Int, String>> = emptyList()
)

class EnigmaViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TmdbRepository()
    private val cwStore = ContinueWatchingStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val playlistStore = PlaylistStore(application)
    private val authService = FirebaseAuthService()
    private val syncService = FirebaseSyncService()

    private val _state = MutableStateFlow(EnigmaUiState())
    val state: StateFlow<EnigmaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(favoritesStore.favorites, playlistStore.playlists, cwStore.entries) { f, p, c ->
                Triple(f, p, c)
            }.collect { (favs, lists, cw) ->
                _state.update { it.copy(favorites = favs, playlists = lists, continueWatching = cw) }
            }
        }
        viewModelScope.launch {
            authService.authState.collect { user ->
                _state.update {
                    it.copy(
                        isLoggedIn = user != null,
                        userEmail = user?.email.orEmpty(),
                        userDisplayName = user?.displayName ?: "Guest"
                    )
                }
                if (user != null) pullCloud()
            }
        }
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            val splashStart = System.currentTimeMillis()
            _state.update { it.copy(contentLoading = true, error = null, searchResults = null) }
            try {
                val rows = repo.buildHomeRows()
                _state.update { it.copy(homeRows = rows) }
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

    fun selectPlaylist(id: String?) {
        _state.update { it.copy(selectedPlaylistId = id, section = NavSection.LISTS) }
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
                        it.copy(contentLoading = false, searchResults = SearchResults(movies.await(), tv.await()), section = NavSection.HOME)
                    }
                }
            } catch (_: Exception) {
                _state.update { it.copy(contentLoading = false, error = "Search failed") }
            }
        }
    }

    fun openMovieDetail(movie: MovieItem) = loadDetail(ContentType.MOVIE, movie.id, movie.title, movie.posterUrl)
    fun openTvDetail(show: TvItem) = loadDetail(ContentType.TV, show.id, show.displayName, show.posterUrl)

    private fun loadDetail(type: ContentType, id: Int, title: String, poster: String?) {
        viewModelScope.launch {
            _state.update { it.copy(showDetail = true, detailLoading = true, detail = null) }
            try {
                val fav = _state.value.favorites.any { it.id == id && it.type == type }
                val detail = when (type) {
                    ContentType.MOVIE -> buildMovieDetail(id, fav)
                    ContentType.TV -> buildTvDetail(id, fav)
                }
                _state.update { it.copy(detailLoading = false, detail = detail) }
            } catch (e: Exception) {
                _state.update { it.copy(detailLoading = false, error = "Could not load details", showDetail = false) }
            }
        }
    }

    private suspend fun buildMovieDetail(id: Int, isFavorite: Boolean): MediaDetailUi {
        val d = repo.movieDetail(id)
        val movie = MovieItem(id, d.title, d.posterPath, d.backdropPath, d.releaseDate, d.voteAverage, d.overview)
        return MediaDetailUi(
            type = ContentType.MOVIE,
            id = id,
            title = d.title,
            overview = d.overview.orEmpty(),
            posterUrl = movie.posterUrl,
            backdropUrl = movie.backdropUrl,
            metaLine = "★ ${"%.1f".format(d.voteAverage)} · ${formatRuntime(d.runtime) ?: "Movie"}",
            releaseLabel = movie.comingSoonLabel(),
            ratingText = "${d.voteAverage}",
            genresText = d.genres.joinToString(" · ") { it.name },
            cast = d.credits?.cast?.take(15) ?: emptyList(),
            isPlayable = movie.isReleased(),
            isFavorite = isFavorite
        )
    }

    private suspend fun buildTvDetail(id: Int, isFavorite: Boolean): MediaDetailUi {
        val d = repo.tvDetail(id)
        val show = TvItem(id, d.name, posterPath = d.posterPath, backdropPath = d.backdropPath, firstAirDate = d.firstAirDate, voteAverage = d.voteAverage, overview = d.overview)
        val seasons = d.seasons.filter { it.seasonNumber > 0 }.map { it.seasonNumber }
        val season = seasons.firstOrNull() ?: 1
        val eps = if (seasons.isNotEmpty()) repo.tvSeason(id, season) else emptyList()
        return MediaDetailUi(
            type = ContentType.TV,
            id = id,
            title = d.name,
            overview = d.overview.orEmpty(),
            posterUrl = show.posterUrl,
            backdropUrl = show.backdropUrl,
            metaLine = "★ ${"%.1f".format(d.voteAverage)} · ${d.numberOfSeasons} seasons",
            releaseLabel = show.comingSoonLabel(),
            ratingText = "${d.voteAverage}",
            genresText = d.genres.joinToString(" · ") { it.name },
            cast = d.credits?.cast?.take(15) ?: emptyList(),
            isPlayable = show.isReleased(),
            seasons = seasons,
            episodes = eps,
            selectedSeason = season,
            selectedEpisode = eps.firstOrNull()?.episodeNumber ?: 1,
            isFavorite = isFavorite
        )
    }

    fun closeDetail() = _state.update { it.copy(showDetail = false, detail = null) }

    fun detailSeasonChange(season: Int) {
        val d = _state.value.detail ?: return
        if (d.type != ContentType.TV) return
        viewModelScope.launch {
            val eps = repo.tvSeason(d.id, season)
            _state.update {
                it.copy(detail = d.copy(seasons = d.seasons, episodes = eps, selectedSeason = season, selectedEpisode = eps.firstOrNull()?.episodeNumber ?: 1))
            }
        }
    }

    fun detailEpisodeSelect(episode: Int) {
        _state.update { st ->
            val d = st.detail ?: return@update st
            st.copy(detail = d.copy(selectedEpisode = episode))
        }
    }

    fun playFromDetail() {
        val d = _state.value.detail ?: return
        closeDetail()
        when (d.type) {
            ContentType.MOVIE -> playMovie(MovieItem(d.id, d.title))
            ContentType.TV -> selectShow(d.id, d.title, d.selectedSeason, d.selectedEpisode)
        }
    }

    fun toggleDetailFavorite() {
        val d = _state.value.detail ?: return
        val item = if (d.type == ContentType.MOVIE) {
            FavoriteItem(d.id, d.title, d.posterUrl ?: "", ContentType.MOVIE)
        } else {
            FavoriteItem(d.id, d.title, d.posterUrl ?: "", ContentType.TV)
        }
        viewModelScope.launch {
            favoritesStore.toggle(item)
            _state.update { st ->
                val detail = st.detail ?: return@update st
                st.copy(detail = detail.copy(isFavorite = !detail.isFavorite))
            }
            syncIfLoggedIn()
        }
    }

    fun playMovie(movie: MovieItem) {
        val (name, url) = StreamSources.movieUrl(0, movie.id)
        _state.update {
            it.copy(
                playerVisible = true, playerLoading = true, playerTitle = movie.title, playerUrl = url,
                playerAccentMovie = true, playingType = ContentType.MOVIE, currentMovieId = movie.id,
                currentShowId = null, sourceIndex = 0,
                sourceLabel = "$name (1/${StreamSources.movieSources.size})"
            )
        }
        viewModelScope.launch {
            cwStore.addOrUpdate(ContinueWatchingEntry(movie.id, movie.title, movie.posterUrl ?: "", 0, 0, ContentType.MOVIE))
        }
    }

    fun playLiveChannel(channel: LiveChannel) {
        _state.update {
            it.copy(
                playerVisible = true, playerLoading = true, playerTitle = channel.name,
                playerUrl = channel.streamUrl, playerAccentMovie = false,
                playingType = null, sourceLabel = "Live · ${channel.category}"
            )
        }
    }

    fun resumeContinue(entry: ContinueWatchingEntry) {
        when (entry.type) {
            ContentType.MOVIE -> playMovie(MovieItem(entry.id, entry.name))
            ContentType.TV -> selectShow(entry.id, entry.name, entry.season, entry.episode)
        }
    }

    fun playFavorite(item: FavoriteItem) {
        when (item.type) {
            ContentType.MOVIE -> openMovieDetail(MovieItem(item.id, item.title))
            ContentType.TV -> openTvDetail(TvItem(item.id, item.title))
        }
    }

    fun toggleFavorite(item: FavoriteItem) {
        viewModelScope.launch {
            favoritesStore.toggle(item)
            syncIfLoggedIn()
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistStore.createPlaylist(name)
            syncIfLoggedIn()
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            playlistStore.deletePlaylist(id)
            if (_state.value.selectedPlaylistId == id) _state.update { it.copy(selectedPlaylistId = null) }
            syncIfLoggedIn()
        }
    }

    fun addToPlaylist(playlistId: String, item: FavoriteItem) {
        viewModelScope.launch {
            playlistStore.addItem(playlistId, item)
            syncIfLoggedIn()
        }
    }

    fun selectShow(id: Int, name: String, resumeSeason: Int? = null, resumeEpisode: Int? = null) {
        viewModelScope.launch {
            val startSeason = resumeSeason ?: 1
            val startEpisode = resumeEpisode ?: 1
            _state.update {
                it.copy(
                    playerVisible = true, playerLoading = true, playerTitle = name,
                    playerAccentMovie = false, playingType = ContentType.TV,
                    currentShowId = id, currentMovieId = null, sourceIndex = 0,
                    selectedSeason = startSeason, selectedEpisode = startEpisode
                )
            }
            try {
                val detail = repo.tvDetail(id)
                val seasons = detail.seasons.filter { it.seasonNumber > 0 }.map { it.seasonNumber }
                val poster = detail.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" } ?: ""
                cwStore.addOrUpdate(ContinueWatchingEntry(id, name, poster, startSeason, startEpisode, ContentType.TV))
                if (seasons.isNotEmpty()) {
                    val season = if (startSeason in seasons) startSeason else seasons.first()
                    loadEpisodesInternal(id, season, startEpisode, play = true)
                    _state.update { it.copy(seasons = seasons, selectedSeason = season) }
                } else playCurrentEpisode()
            } catch (_: Exception) {
                playCurrentEpisode()
            }
        }
    }

    private suspend fun loadEpisodesInternal(showId: Int, season: Int, resumeEpisode: Int?, play: Boolean) {
        val eps = repo.tvSeason(showId, season)
        val episodeList = eps.map { it.episodeNumber to it.name }
        val episode = resumeEpisode?.takeIf { n -> episodeList.any { it.first == n } } ?: episodeList.firstOrNull()?.first ?: 1
        _state.update { it.copy(episodes = episodeList, selectedSeason = season, selectedEpisode = episode) }
        if (play) playCurrentEpisode()
    }

    fun onSeasonChange(season: Int) {
        val showId = _state.value.currentShowId ?: return
        viewModelScope.launch { loadEpisodesInternal(showId, season, null, play = true) }
    }

    fun onEpisodeChange(episode: Int) {
        _state.update { it.copy(selectedEpisode = episode) }
        playCurrentEpisode()
    }

    private fun playCurrentEpisode() {
        val s = _state.value
        val showId = s.currentShowId ?: return
        val (name, url) = StreamSources.tvUrl(s.sourceIndex, showId, s.selectedSeason, s.selectedEpisode)
        _state.update {
            it.copy(playerLoading = true, playerUrl = url, sourceLabel = "$name (${(s.sourceIndex % StreamSources.tvSources.size) + 1}/${StreamSources.tvSources.size})")
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
                _state.update { it.copy(playerLoading = true, sourceIndex = next, playerUrl = url, sourceLabel = "$name (${next + 1}/${StreamSources.movieSources.size})") }
            }
            ContentType.TV -> {
                _state.update { it.copy(sourceIndex = it.sourceIndex + 1) }
                playCurrentEpisode()
            }
            null -> {
                _state.update { it.copy(sourceIndex = it.sourceIndex + 1) }
            }
        }
    }

    fun onPlayerPageLoading(loading: Boolean) = _state.update { it.copy(playerLoading = loading) }

    fun closePlayer() = _state.update { it.copy(playerVisible = false, playerUrl = "", playerLoading = false, playingType = null) }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null) }
            authService.signIn(email, password)
                .onSuccess { _state.update { it.copy(profileMessage = "Signed in") }; syncIfLoggedIn() }
                .onFailure { e -> _state.update { it.copy(profileError = e.message ?: "Sign in failed") } }
        }
    }

    fun signUp(email: String, password: String, name: String) {
        viewModelScope.launch {
            authService.signUp(email, password, name)
                .onSuccess { _state.update { it.copy(profileMessage = "Account created") }; syncIfLoggedIn() }
                .onFailure { e -> _state.update { it.copy(profileError = e.message ?: "Sign up failed") } }
        }
    }

    fun signInGuest() {
        viewModelScope.launch {
            authService.signInGuest()
                .onSuccess { _state.update { it.copy(profileMessage = "Guest session") } }
                .onFailure { e -> _state.update { it.copy(profileError = e.message ?: "Guest sign in failed") } }
        }
    }

    fun signOut() {
        authService.signOut()
        _state.update { it.copy(profileMessage = "Signed out") }
    }

    fun syncToCloud() {
        viewModelScope.launch {
            syncIfLoggedIn()
            _state.update { it.copy(profileMessage = "Synced to Firebase") }
        }
    }

    private suspend fun syncIfLoggedIn() {
        if (!_state.value.isLoggedIn) return
        val s = _state.value
        syncService.saveProfile(s.userDisplayName, s.userEmail)
        syncService.pushFavorites(s.favorites)
        syncService.pushContinueWatching(s.continueWatching)
        syncService.pushPlaylists(s.playlists)
    }

    private suspend fun pullCloud() {
        val cloud = syncService.pullAll() ?: return
        if (cloud.favorites.isNotEmpty()) favoritesStore.replaceAll(cloud.favorites)
        if (cloud.continueWatching.isNotEmpty()) cwStore.replaceAll(cloud.continueWatching)
        if (cloud.playlists.isNotEmpty()) playlistStore.replaceAll(cloud.playlists)
        _state.update { it.copy(profileMessage = "Cloud data loaded", userDisplayName = cloud.profile.displayName.ifBlank { it.userDisplayName }) }
    }
}
