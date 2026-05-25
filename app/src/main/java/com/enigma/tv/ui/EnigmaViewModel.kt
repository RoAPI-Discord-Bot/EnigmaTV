package com.enigma.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.ContinueWatchingStore
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.HomeRow
import com.enigma.tv.data.IptvChannel
import com.enigma.tv.data.IptvRepository
import com.enigma.tv.data.LiveChannelFavoritesStore
import com.enigma.tv.data.LiveSportMatch
import com.enigma.tv.data.LiveStreamLink
import com.enigma.tv.data.LiveTvBrowseState
import com.enigma.tv.data.LiveTvTab
import com.enigma.tv.data.StreamedRepository
import com.enigma.tv.data.MediaDetailUi
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.Playlist
import com.enigma.tv.data.PlaylistStore
import com.enigma.tv.data.SearchResults
import com.enigma.tv.data.StreamSources
import com.enigma.tv.data.TmdbRepository
import com.enigma.tv.data.TvItem
import com.enigma.tv.data.UserSessionStore
import com.enigma.tv.data.canStream
import com.enigma.tv.data.comingSoonLabel
import com.enigma.tv.data.firebase.FirebaseAuthService
import com.enigma.tv.data.firebase.FirebaseSyncService
import com.enigma.tv.data.formatRuntime
import com.enigma.tv.data.FavoritesStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val showAuthGate: Boolean = true,
    val authLoading: Boolean = false,
    val showSplash: Boolean = false,
    val contentLoading: Boolean = false,
    val section: NavSection = NavSection.HOME,
    val homeRows: List<HomeRow> = emptyList(),
    val continueWatching: List<ContinueWatchingEntry> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val liveTv: LiveTvBrowseState = LiveTvBrowseState(),
    val playerHls: Boolean = false,
    val showLiveStreamPicker: Boolean = false,
    val liveStreamPicker: List<LiveStreamLink> = emptyList(),
    val searchResults: SearchResults? = null,
    val error: String? = null,
    val showDetail: Boolean = false,
    val detailLoading: Boolean = false,
    val detail: MediaDetailUi? = null,
    val playerVisible: Boolean = false,
    val playerLoading: Boolean = false,
    val playerTitle: String = "",
    val playerUrl: String = "",
    val playerLogoUrl: String? = null,
    val playerResolveToken: Int = 0,
    val playerAccentMovie: Boolean = true,
    val playerLiveTv: Boolean = false,
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
    private val sessionStore = UserSessionStore(application)
    private val authService = FirebaseAuthService()
    private val syncService = FirebaseSyncService()
    private val iptvRepo = IptvRepository()
    private val streamedRepo = StreamedRepository()
    private val liveChannelStore = LiveChannelFavoritesStore(application)

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
            combine(liveChannelStore.favoriteIds, liveChannelStore.recentIds) { favs, recents ->
                favs to recents
            }.collect { (favs, recentIds) ->
                _state.update { st ->
                    val recent = iptvRepo.resolveByIds(st.liveTv.channels, recentIds)
                    st.copy(
                        liveTv = applyChannelFilters(
                            st.liveTv.copy(
                                favoriteChannelIds = favs,
                                recentChannels = recent
                            )
                        )
                    )
                }
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
        viewModelScope.launch {
            val done = sessionStore.isOnboardingComplete()
            _state.update {
                it.copy(
                    showAuthGate = !done,
                    showSplash = false,
                    contentLoading = done && it.homeRows.isEmpty()
                )
            }
            if (done) loadHome()
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            _state.update { it.copy(contentLoading = it.homeRows.isEmpty(), error = null, searchResults = null) }
            try {
                val rows = repo.buildHomeRows()
                _state.update { it.copy(homeRows = rows, contentLoading = false) }
            } catch (_: Exception) {
                _state.update { it.copy(contentLoading = false, error = "Could not load EnigmaTV content") }
            }
        }
    }

    private suspend fun finishOnboarding() {
        sessionStore.setOnboardingComplete()
        _state.update { it.copy(showAuthGate = false, authLoading = false, profileError = null) }
        if (_state.value.homeRows.isEmpty()) loadHome()
    }

    fun setSection(section: NavSection) {
        _state.update { it.copy(section = section, searchResults = null, error = null) }
        if (section == NavSection.LIVE) {
            val live = _state.value.liveTv
            if (live.channels.isEmpty() && live.events.isEmpty() && !live.loading) loadLiveTv()
        }
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
            isPlayable = movie.canStream(),
            isFavorite = isFavorite
        )
    }

    private suspend fun buildTvDetail(id: Int, isFavorite: Boolean): MediaDetailUi {
        val d = repo.tvDetail(id)
        val show = TvItem(id, d.name, posterPath = d.posterPath, backdropPath = d.backdropPath, firstAirDate = d.firstAirDate, voteAverage = d.voteAverage, overview = d.overview)
        val seasons = d.seasons.filter { it.seasonNumber > 0 }.map { it.seasonNumber }.ifEmpty { listOf(1) }
        val season = seasons.first()
        val eps = runCatching { repo.tvSeason(id, season) }.getOrDefault(emptyList())
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
            isPlayable = show.canStream(),
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
                playerVisible = true,
                playerLoading = true,
                playerHls = false,
                playerTitle = movie.title,
                playerUrl = url,
                playerLogoUrl = movie.posterUrl,
                playerResolveToken = it.playerResolveToken + 1,
                playerAccentMovie = true,
                playerLiveTv = false,
                playingType = ContentType.MOVIE,
                currentMovieId = movie.id,
                currentShowId = null,
                sourceIndex = 0,
                sourceLabel = "$name (1/${StreamSources.movieSources.size})"
            )
        }
        viewModelScope.launch {
            cwStore.addOrUpdate(ContinueWatchingEntry(movie.id, movie.title, movie.posterUrl ?: "", 0, 0, ContentType.MOVIE))
        }
    }

    fun loadLiveTv() {
        viewModelScope.launch {
            _state.update { it.copy(liveTv = it.liveTv.copy(loading = true, error = null)) }
            try {
                coroutineScope {
                    val events = async { streamedRepo.loadEvents() }
                    val channels = async { iptvRepo.loadChannels() }
                    val ev = events.await()
                    val ch = channels.await()
                    val groups = iptvRepo.channelGroups(ch)
                    _state.update { st ->
                        st.copy(
                            liveTv = applyChannelFilters(
                                st.liveTv.copy(
                                    loading = false,
                                    events = ev,
                                    channels = ch,
                                    channelGroups = groups,
                                    filteredEvents = ev
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(liveTv = it.liveTv.copy(loading = false, error = "Could not load live TV: ${e.message}"))
                }
            }
        }
    }

    fun setLiveTvTab(tab: LiveTvTab) {
        _state.update { it.copy(liveTv = it.liveTv.copy(tab = tab)) }
    }

    fun searchLiveTv(query: String) {
        _state.update { st ->
            val live = applyChannelFilters(st.liveTv.copy(searchQuery = query))
            val events = if (query.isBlank()) live.events else streamedRepo.search(live.events, query)
            st.copy(liveTv = live.copy(filteredEvents = events))
        }
    }

    fun setLiveChannelGroupFilter(group: String?) {
        _state.update { st ->
            st.copy(liveTv = applyChannelFilters(st.liveTv.copy(channelGroupFilter = group)))
        }
    }

    fun toggleLiveFavoritesOnly() {
        _state.update { st ->
            st.copy(liveTv = applyChannelFilters(st.liveTv.copy(favoritesOnly = !st.liveTv.favoritesOnly)))
        }
    }

    fun toggleLiveChannelFavorite(channel: IptvChannel) {
        viewModelScope.launch { liveChannelStore.toggleFavorite(channel.id) }
    }

    fun liveQuickPick(query: String) = searchLiveTv(query)

    private fun applyChannelFilters(live: LiveTvBrowseState): LiveTvBrowseState {
        var channels = live.channels
        if (live.searchQuery.isNotBlank()) channels = iptvRepo.search(channels, live.searchQuery)
        channels = iptvRepo.filterByGroup(channels, live.channelGroupFilter)
        if (live.favoritesOnly) channels = channels.filter { live.favoriteChannelIds.contains(it.id) }
        return live.copy(filteredChannels = channels)
    }

    fun playIptvChannel(channel: IptvChannel) {
        viewModelScope.launch { liveChannelStore.addRecent(channel.id) }
        _state.update {
            it.copy(
                playerVisible = true,
                playerHls = true,
                playerLiveTv = false,
                playerLoading = true,
                playerTitle = channel.name,
                playerUrl = channel.streamUrl,
                playerLogoUrl = channel.logoUrl,
                playerAccentMovie = false,
                playingType = null,
                sourceLabel = "Live · ${channel.group}",
                showLiveStreamPicker = false
            )
        }
    }

    fun playLiveMatch(match: LiveSportMatch) {
        val source = match.sources.firstOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(contentLoading = true) }
            try {
                val streams = streamedRepo.fetchStreams(source.source, source.id)
                _state.update { it.copy(contentLoading = false) }
                when {
                    streams.isEmpty() -> _state.update { it.copy(error = "No streams available for this event") }
                    streams.size == 1 -> playLiveEmbed(match.title, streams.first().embedUrl, streams.first().label)
                    else -> _state.update {
                        it.copy(showLiveStreamPicker = true, liveStreamPicker = streams, playerTitle = match.title)
                    }
                }
            } catch (_: Exception) {
                _state.update { it.copy(contentLoading = false, error = "Could not load streams") }
            }
        }
    }

    fun pickLiveStream(link: LiveStreamLink) {
        val st = _state.value
        val title = st.playerTitle.ifBlank { "Live Event" }
        val index = st.liveStreamPicker.indexOfFirst { it.embedUrl == link.embedUrl }.coerceAtLeast(0)
        _state.update { it.copy(showLiveStreamPicker = false, sourceIndex = index) }
        playLiveEmbed(title, link.embedUrl, link.label)
    }

    fun dismissLiveStreamPicker() {
        _state.update { it.copy(showLiveStreamPicker = false, liveStreamPicker = emptyList()) }
    }

    private fun playLiveEmbed(title: String, embedUrl: String, label: String) {
        _state.update {
            it.copy(
                playerVisible = true,
                playerHls = false,
                playerLiveTv = true,
                playerLoading = true,
                playerTitle = title,
                playerUrl = embedUrl,
                playerAccentMovie = false,
                playingType = null,
                sourceLabel = "Live · $label",
                sourceIndex = 0,
                showLiveStreamPicker = false
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
                    playerVisible = true,
                    playerLoading = true,
                    playerHls = false,
                    playerTitle = name,
                    playerResolveToken = it.playerResolveToken + 1,
                    playerAccentMovie = false,
                    playerLiveTv = false,
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
                val poster = detail.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" } ?: ""
                val posterUrl = detail.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
                cwStore.addOrUpdate(ContinueWatchingEntry(id, name, poster, startSeason, startEpisode, ContentType.TV))
                _state.update { it.copy(playerLogoUrl = posterUrl) }
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
            it.copy(
                playerLoading = true,
                playerHls = false,
                playerUrl = url,
                playerResolveToken = it.playerResolveToken + 1,
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
                        playerResolveToken = it.playerResolveToken + 1,
                        sourceLabel = "$name (${next + 1}/${StreamSources.movieSources.size})"
                    )
                }
            }
            ContentType.TV -> {
                _state.update { it.copy(sourceIndex = it.sourceIndex + 1, playerResolveToken = it.playerResolveToken + 1) }
                playCurrentEpisode()
            }
            null -> {
                val st = _state.value
                if (st.playerLiveTv && st.liveStreamPicker.size > 1) {
                    val next = (st.sourceIndex + 1) % st.liveStreamPicker.size
                    val link = st.liveStreamPicker[next]
                    playLiveEmbed(st.playerTitle, link.embedUrl, link.label)
                    _state.update { it.copy(sourceIndex = next) }
                } else {
                    _state.update { it.copy(sourceIndex = it.sourceIndex + 1) }
                }
            }
        }
    }

    fun onPlayerPageLoading(loading: Boolean) = _state.update { it.copy(playerLoading = loading) }

    fun closePlayer() = _state.update {
        it.copy(
            playerVisible = false,
            playerUrl = "",
            playerLogoUrl = null,
            playerResolveToken = 0,
            playerLoading = false,
            playingType = null,
            playerLiveTv = false,
            playerHls = false
        )
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null, authLoading = true) }
            authService.signIn(email, password)
                .onSuccess {
                    _state.update { it.copy(profileMessage = "Signed in") }
                    syncIfLoggedIn()
                    finishOnboarding()
                }
                .onFailure { e ->
                    _state.update { it.copy(profileError = authErrorMessage(e), authLoading = false) }
                }
        }
    }

    fun signUp(email: String, password: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null, authLoading = true) }
            authService.signUp(email, password, name)
                .onSuccess {
                    _state.update { it.copy(profileMessage = "Account created") }
                    syncIfLoggedIn()
                    finishOnboarding()
                }
                .onFailure { e ->
                    _state.update { it.copy(profileError = authErrorMessage(e), authLoading = false) }
                }
        }
    }

    fun signInGuest() {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null, authLoading = true) }
            authService.signInGuest()
                .onSuccess {
                    _state.update { it.copy(profileMessage = "Guest session") }
                    finishOnboarding()
                }
                .onFailure { e ->
                    _state.update { it.copy(profileError = authErrorMessage(e), authLoading = false) }
                }
        }
    }

    private fun authErrorMessage(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                "Firebase not configured. Ensure app/google-services.json is in the project and Email + Anonymous auth are enabled in Firebase Console."
            msg.contains("API key", ignoreCase = true) -> "Firebase API key issue — check google-services.json package name is com.enigmatv"
            msg.isNotBlank() -> msg
            else -> "Authentication failed"
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
