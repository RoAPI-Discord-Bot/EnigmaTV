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
import com.enigma.tv.data.LiveEmbedResolver
import com.enigma.tv.data.StreamedRepository
import com.enigma.tv.data.MediaDetailUi
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.Playlist
import com.enigma.tv.data.PlaylistStore
import com.enigma.tv.data.ProfileStore
import com.enigma.tv.data.SearchResults
import com.enigma.tv.data.StreamSources
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.data.TmdbRepository
import com.enigma.tv.data.TvItem
import com.enigma.tv.data.UserSessionStore
import com.enigma.tv.data.canStream
import com.enigma.tv.data.comingSoonLabel
import com.enigma.tv.data.firebase.FirebaseAuthService
import com.enigma.tv.data.firebase.FirebaseSyncService
import com.enigma.tv.data.formatRuntime
import com.enigma.tv.data.FavoritesStore
import com.enigma.tv.data.ImgurUploadService
import com.enigma.tv.data.ProfileImageStorage
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val sessionReady: Boolean = false,
    val showAuthGate: Boolean = false,
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
    val playerStreamFailed: Boolean = false,
    val playbackProgressPercent: Int = 0,
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
    val episodes: List<Pair<Int, String>> = emptyList(),
    val profiles: List<ViewerProfile> = emptyList(),
    val activeProfileId: String = "default",
    val showProfilePicker: Boolean = false
)

class EnigmaViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TmdbRepository()
    private val cwStore = ContinueWatchingStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val playlistStore = PlaylistStore(application)
    private val sessionStore = UserSessionStore(application)
    private val profileStore = ProfileStore(application)
    private val authService = FirebaseAuthService()
    private val syncService = FirebaseSyncService()
    private val iptvRepo = IptvRepository()
    private val streamedRepo = StreamedRepository()
    private val liveChannelStore = LiveChannelFavoritesStore(application)
    private val imageLoader = ImageLoader(application)

    private val _state = MutableStateFlow(EnigmaUiState())
    val state: StateFlow<EnigmaUiState> = _state.asStateFlow()
    private var syncJob: Job? = null
    private var tvNavJob: Job? = null
    private var homeLoadJob: Job? = null

    private fun activeProfileId(): String = _state.value.activeProfileId.ifBlank { "default" }

    private fun scheduleCloudSync() {
        if (!_state.value.isLoggedIn) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1200)
            syncIfLoggedIn()
        }
    }

    private fun syncProfilesImmediately() {
        viewModelScope.launch {
            if (!_state.value.isLoggedIn) {
                _state.update {
                    it.copy(profileMessage = "Saved on this device. Sign in to sync profiles to the cloud.")
                }
                return@launch
            }
            val ok = syncIfLoggedIn()
            _state.update {
                it.copy(
                    profileMessage = if (ok) "Profile saved to cloud" else "Could not sync profile — check connection",
                    profileError = if (ok) null else it.profileError
                )
            }
        }
    }

    init {
        viewModelScope.launch { profileStore.ensureDefaultProfile() }
        viewModelScope.launch {
            combine(profileStore.profiles, profileStore.activeProfileId) { profiles, activeId ->
                profiles to activeId
            }.collect { (profiles, activeId) ->
                _state.update { it.copy(profiles = profiles, activeProfileId = activeId) }
            }
        }
        viewModelScope.launch {
            profileStore.activeProfileId.flatMapLatest { profileId ->
                combine(
                    favoritesStore.watch(profileId),
                    playlistStore.watch(profileId),
                    cwStore.watch(profileId)
                ) { f, p, c -> Triple(f, p, c) }
            }.collect { (favs, lists, cw) ->
                _state.update { it.copy(favorites = favs, playlists = lists, continueWatching = cw) }
                scheduleCloudSync()
                viewModelScope.launch {
                    val profileId = _state.value.activeProfileId
                    val enriched = enrichContinueWatchingPosters(profileId, cw)
                    if (enriched != cw) {
                        _state.update { it.copy(continueWatching = enriched) }
                    }
                }
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
                if (user != null) {
                    viewModelScope.launch { pullCloudSafe() }
                }
            }
        }
        viewModelScope.launch {
            profileStore.ensureDefaultProfile()
            val done = sessionStore.isOnboardingComplete()
            val profiles = profileStore.profiles.first()
            val activeId = profileStore.activeProfileId.first()
            _state.update {
                it.copy(
                    profiles = profiles,
                    activeProfileId = activeId.ifBlank { it.activeProfileId },
                    sessionReady = true,
                    showAuthGate = !done,
                    showSplash = false,
                    showProfilePicker = done,
                    contentLoading = false
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileStore.setActive(profileId)
            pullCloudSafe()
        }
    }

    fun selectProfileAndContinue(profileId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    contentLoading = true,
                    homeRows = emptyList(),
                    error = null
                )
            }
            profileStore.setActive(profileId)
            _state.update { it.copy(showProfilePicker = false) }
            pullCloudSafe()
            loadHome()
        }
    }

    fun dismissProfilePicker() {
        _state.update {
            it.copy(
                showProfilePicker = false,
                contentLoading = it.homeRows.isEmpty(),
                homeRows = if (it.homeRows.isEmpty()) emptyList() else it.homeRows
            )
        }
        if (_state.value.homeRows.isEmpty()) loadHome()
    }

    fun showProfilePickerScreen() {
        _state.update {
            it.copy(
                showProfilePicker = true,
                playerVisible = false,
                showDetail = false
            )
        }
    }

    fun addProfile(name: String) {
        viewModelScope.launch {
            profileStore.addProfile(name)
            syncProfilesImmediately()
        }
    }

    fun removeProfile(profileId: String) {
        viewModelScope.launch {
            profileStore.removeProfile(profileId)
            syncProfilesImmediately()
        }
    }

    fun renameProfile(profileId: String, name: String) {
        viewModelScope.launch {
            profileStore.renameProfile(profileId, name)
            syncProfilesImmediately()
        }
    }

    fun setProfileAvatarIndex(profileId: String, index: Int) {
        viewModelScope.launch {
            profileStore.setProfileAvatarIndex(profileId, index)
            syncProfilesImmediately()
        }
    }

    fun setProfileAvatarUri(profileId: String, uri: String?) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null) }
            if (uri.isNullOrBlank()) {
                profileStore.setProfileAvatarData(profileId, avatarUri = null, avatarBase64 = null)
                syncProfilesImmediately()
                return@launch
            }
            val base64 = ProfileImageStorage.persistAndEncode(getApplication(), profileId, uri)
            if (base64.isNullOrBlank()) {
                _state.update { it.copy(profileError = "Could not read that photo") }
                return@launch
            }
            val imgurUrl = ImgurUploadService.uploadBase64Jpeg(base64)
            profileStore.setProfileAvatarData(
                profileId,
                avatarUri = imgurUrl ?: uri,
                avatarBase64 = if (imgurUrl != null) null else base64
            )
            syncProfilesImmediately()
        }
    }

    private suspend fun enrichContinueWatchingPosters(
        profileId: String,
        list: List<ContinueWatchingEntry>
    ): List<ContinueWatchingEntry> {
        var changed = false
        val out = list.map { entry ->
            if (entry.poster.isNotBlank() || entry.type != ContentType.MOVIE) return@map entry
            try {
                val detail = repo.movieDetail(entry.id)
                val url = detail.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" } ?: ""
                if (url.isBlank()) entry
                else {
                    changed = true
                    val updated = entry.copy(poster = url)
                    cwStore.addOrUpdate(profileId, updated)
                    updated
                }
            } catch (_: Exception) {
                entry
            }
        }
        return if (changed) out else list
    }

    fun loadHome() {
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            _state.update { it.copy(contentLoading = it.homeRows.isEmpty(), error = null, searchResults = null) }
            try {
                val rows = repo.buildHomeRows()
                _state.update {
                    it.copy(
                        homeRows = rows,
                        contentLoading = false,
                        error = if (rows.isEmpty()) "Could not load EnigmaTV content. Check connection and retry." else null
                    )
                }
                if (rows.isNotEmpty()) prefetchHomePosters(rows)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        contentLoading = false,
                        error = "Could not load EnigmaTV content. Check connection and retry."
                    )
                }
            }
        }
    }

    private suspend fun finishOnboarding() {
        sessionStore.setOnboardingComplete()
        _state.update {
            it.copy(
                showAuthGate = false,
                authLoading = false,
                profileError = null,
                showProfilePicker = true,
                contentLoading = false
            )
        }
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
        val keepEp = d.selectedEpisode
        viewModelScope.launch {
            val eps = repo.tvSeason(d.id, season)
            val episodeList = eps.map { it.episodeNumber to it.name }.sortedBy { it.first }
            val episode = pickEpisodeNumber(episodeList, keepEp)
            _state.update {
                it.copy(
                    detail = d.copy(
                        seasons = d.seasons,
                        episodes = eps,
                        selectedSeason = season,
                        selectedEpisode = episode
                    )
                )
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
            favoritesStore.toggle(activeProfileId(), item)
            _state.update { st ->
                val detail = st.detail ?: return@update st
                st.copy(detail = detail.copy(isFavorite = !detail.isFavorite))
            }
            syncIfLoggedIn()
        }
    }

    private fun prefetchHomePosters(rows: List<HomeRow>) {
        val urls = rows.flatMap { row ->
            when (row) {
                is HomeRow.Movies -> row.items.mapNotNull { it.posterUrl }
                is HomeRow.TvShows -> row.items.mapNotNull { it.posterUrl }
            }
        }.distinct().take(36)
        urls.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(getApplication())
                    .data(url)
                    .size(342, 513)
                    .build()
            )
        }
    }

    fun playMovie(movie: MovieItem, posterOverride: String? = null) {
        val poster = posterOverride?.takeIf { it.isNotBlank() } ?: movie.posterUrl
        val (name, url) = StreamSources.movieUrl(0, movie.id)
        _state.update {
            it.copy(
                playerVisible = true,
                playerLoading = false,
                playerHls = false,
                playerTitle = movie.title,
                playerUrl = url,
                playerLogoUrl = poster,
                playerResolveToken = it.playerResolveToken + 1,
                playerAccentMovie = true,
                playerLiveTv = false,
                playingType = ContentType.MOVIE,
                currentMovieId = movie.id,
                currentShowId = null,
                sourceIndex = 0,
                sourceLabel = "Enigma Player · $name (1/${StreamSources.movieSources.size})"
            )
        }
        viewModelScope.launch {
            cwStore.addOrUpdate(
                activeProfileId(),
                ContinueWatchingEntry(movie.id, movie.title, poster ?: "", 0, 0, ContentType.MOVIE)
            )
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
                    else -> {
                        _state.update { it.copy(liveStreamPicker = streams, playerTitle = match.title) }
                        if (streams.size == 1) {
                            playLiveEmbed(match.title, streams.first().embedUrl, streams.first().label)
                        } else {
                            _state.update { it.copy(showLiveStreamPicker = true) }
                        }
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
        viewModelScope.launch {
            _state.update {
                it.copy(
                    playerVisible = true,
                    playerHls = false,
                    playerLiveTv = true,
                    playerLoading = true,
                    playerStreamFailed = false,
                    playerTitle = title,
                    playerAccentMovie = false,
                    playingType = null,
                    sourceLabel = "Live · $label",
                    showLiveStreamPicker = false,
                    error = null
                )
            }
            var playable: String? = null
            for (candidate in StreamedRepository.embedCandidates(embedUrl)) {
                val resolved = LiveEmbedResolver.resolvePlayableUrl(candidate)
                if (resolved.isNullOrBlank()) continue
                if (resolved.contains(".m3u8", ignoreCase = true)) {
                    playable = resolved
                    break
                }
                if (!LiveEmbedResolver.isUnplayableContent(resolved)) {
                    playable = resolved
                    break
                }
            }
            if (playable == null) {
                _state.update {
                    it.copy(
                        playerLoading = false,
                        playerStreamFailed = true,
                        playerVisible = true,
                        playerUrl = embedUrl,
                        error = null
                    )
                }
                return@launch
            }
            if (playable.contains(".m3u8", ignoreCase = true)) {
                playLiveNativeStream(playable)
                return@launch
            }
            _state.update {
                it.copy(
                    playerUrl = playable,
                    playerResolveToken = it.playerResolveToken + 1,
                    playerLoading = true,
                    playerStreamFailed = false
                )
            }
        }
    }

    fun onPlayerStreamFailed() {
        _state.update {
            it.copy(playerLoading = false, playerStreamFailed = true)
        }
    }

    fun clearPlayerStreamFailed() {
        _state.update { it.copy(playerStreamFailed = false, playerLoading = true) }
    }

    fun playLiveNativeStream(streamUrl: String) {
        if (streamUrl.isBlank()) return
        _state.update {
            it.copy(
                playerVisible = true,
                playerHls = true,
                playerLiveTv = false,
                playerLoading = true,
                playerUrl = streamUrl,
                sourceLabel = it.sourceLabel.ifBlank { "Live" },
                error = null
            )
        }
    }

    fun resumeContinue(entry: ContinueWatchingEntry) {
        when (entry.type) {
            ContentType.MOVIE -> playMovie(MovieItem(entry.id, entry.name), entry.poster)
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
            favoritesStore.toggle(activeProfileId(), item)
            syncIfLoggedIn()
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistStore.createPlaylist(activeProfileId(), name)
            syncIfLoggedIn()
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            playlistStore.deletePlaylist(activeProfileId(), id)
            if (_state.value.selectedPlaylistId == id) _state.update { it.copy(selectedPlaylistId = null) }
            syncIfLoggedIn()
        }
    }

    fun addToPlaylist(playlistId: String, item: FavoriteItem) {
        viewModelScope.launch {
            playlistStore.addItem(activeProfileId(), playlistId, item)
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
                    playerLoading = false,
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
                val posterUrl = detail.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
                cwStore.addOrUpdate(
                    activeProfileId(),
                    ContinueWatchingEntry(id, name, posterUrl ?: "", startSeason, startEpisode, ContentType.TV)
                )
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

    private fun pickEpisodeNumber(episodeList: List<Pair<Int, String>>, requested: Int?): Int {
        if (episodeList.isEmpty()) return 1
        if (requested != null && episodeList.any { it.first == requested }) return requested
        if (requested != null) {
            return episodeList.filter { it.first <= requested }.maxByOrNull { it.first }?.first
                ?: episodeList.first().first
        }
        return episodeList.first().first
    }

    private suspend fun loadEpisodesInternal(showId: Int, season: Int, resumeEpisode: Int?, play: Boolean) {
        val eps = repo.tvSeason(showId, season)
        val episodeList = eps.map { it.episodeNumber to it.name }.sortedBy { it.first }
        val episode = pickEpisodeNumber(episodeList, resumeEpisode)
        _state.update { it.copy(episodes = episodeList, selectedSeason = season, selectedEpisode = episode) }
        if (play) playCurrentEpisode()
    }

    fun onSeasonChange(season: Int) {
        val showId = _state.value.currentShowId ?: return
        val keepEpisode = _state.value.selectedEpisode
        tvNavJob?.cancel()
        tvNavJob = viewModelScope.launch {
            _state.update { it.copy(playerLoading = true) }
            loadEpisodesInternal(showId, season, keepEpisode, play = true)
        }
    }

    fun onEpisodeChange(episode: Int) {
        val showId = _state.value.currentShowId ?: return
        val season = _state.value.selectedSeason
        tvNavJob?.cancel()
        tvNavJob = viewModelScope.launch {
            val list = _state.value.episodes
            if (list.none { it.first == episode }) {
                loadEpisodesInternal(showId, season, episode, play = false)
            } else {
                _state.update { it.copy(selectedEpisode = episode) }
            }
            playCurrentEpisode()
            persistContinueWatching()
        }
    }

    fun playAdjacentEpisode(forward: Boolean) {
        val s = _state.value
        if (s.playingType != ContentType.TV) return
        val nums = s.episodes.map { it.first }.sorted()
        if (nums.isEmpty()) return
        val idx = nums.indexOf(s.selectedEpisode)
        val targetIdx = when {
            forward && idx >= 0 && idx < nums.lastIndex -> idx + 1
            !forward && idx > 0 -> idx - 1
            forward && idx < 0 -> 0
            else -> return
        }
        onEpisodeChange(nums[targetIdx])
    }

    fun hasAdjacentEpisode(forward: Boolean): Boolean {
        val s = _state.value
        if (s.playingType != ContentType.TV) return false
        val nums = s.episodes.map { it.first }.sorted()
        if (nums.isEmpty()) return false
        val idx = nums.indexOf(s.selectedEpisode)
        return if (forward) idx >= 0 && idx < nums.lastIndex else idx > 0
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
                sourceLabel = "S${s.selectedSeason}E${s.selectedEpisode} · $name (${(s.sourceIndex % StreamSources.tvSources.size) + 1}/${StreamSources.tvSources.size})"
            )
        }
        viewModelScope.launch {
            persistContinueWatching()
            delay(400)
            _state.update { it.copy(playerLoading = false) }
        }
    }

    fun onPlaybackProgress(percent: Int) {
        val pct = percent.coerceIn(0, 100)
        _state.update { it.copy(playbackProgressPercent = pct) }
        if (pct >= 5 && (pct % 10 == 0 || pct >= 90)) {
            viewModelScope.launch { persistContinueWatching() }
        }
    }

    fun onEpisodeFinished() {
        if (_state.value.playingType != ContentType.TV) return
        if (!hasAdjacentEpisode(forward = true)) return
        playAdjacentEpisode(forward = true)
    }

    private suspend fun persistContinueWatching() {
        val s = _state.value
        val profileId = activeProfileId()
        when (s.playingType) {
            ContentType.MOVIE -> {
                val id = s.currentMovieId ?: return
                cwStore.addOrUpdate(
                    profileId,
                    ContinueWatchingEntry(
                        id = id,
                        name = s.playerTitle,
                        poster = s.playerLogoUrl ?: "",
                        season = 0,
                        episode = 0,
                        type = ContentType.MOVIE,
                        progressPercent = s.playbackProgressPercent
                    )
                )
            }
            ContentType.TV -> {
                val id = s.currentShowId ?: return
                cwStore.addOrUpdate(
                    profileId,
                    ContinueWatchingEntry(
                        id = id,
                        name = s.playerTitle,
                        poster = s.playerLogoUrl ?: "",
                        season = s.selectedSeason,
                        episode = s.selectedEpisode,
                        type = ContentType.TV
                    )
                )
            }
            else -> return
        }
        syncIfLoggedIn()
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
                        playerLoading = false,
                        sourceIndex = next,
                        playerUrl = url,
                        playerResolveToken = it.playerResolveToken + 1,
                        sourceLabel = "Enigma Player · $name (${next + 1}/${StreamSources.movieSources.size})"
                    )
                }
            }
            ContentType.TV -> {
                _state.update { it.copy(sourceIndex = it.sourceIndex + 1, playerResolveToken = it.playerResolveToken + 1) }
                playCurrentEpisode()
            }
            null -> {
                val st = _state.value
                if (st.liveStreamPicker.size > 1) {
                    val next = (st.sourceIndex + 1) % st.liveStreamPicker.size
                    val link = st.liveStreamPicker[next]
                    clearPlayerStreamFailed()
                    playLiveEmbed(st.playerTitle, link.embedUrl, link.label)
                    _state.update { it.copy(sourceIndex = next, playerHls = false, playerLiveTv = true) }
                } else if (st.playerUrl.isNotBlank()) {
                    val bumped = StreamedRepository.bumpStreamNumber(st.playerUrl)
                    if (bumped != null) {
                        clearPlayerStreamFailed()
                        playLiveEmbed(st.playerTitle, bumped, st.sourceLabel.substringAfter("· ").ifBlank { "Stream" })
                    }
                } else {
                    _state.update { it.copy(sourceIndex = it.sourceIndex + 1) }
                }
            }
        }
    }

    fun onPlayerPageLoading(loading: Boolean) {
        _state.update {
            it.copy(
                playerLoading = loading,
                playerStreamFailed = if (loading) false else it.playerStreamFailed
            )
        }
    }

    fun onPlayerPlaybackReady() {
        _state.update { it.copy(playerLoading = false, playerStreamFailed = false) }
    }

    fun closePlayer() {
        viewModelScope.launch { persistContinueWatching() }
        _state.update {
            it.copy(
                playerVisible = false,
                playerUrl = "",
                playerLogoUrl = null,
                playerResolveToken = 0,
                playerLoading = false,
                playerStreamFailed = false,
                playbackProgressPercent = 0,
                playingType = null,
                playerLiveTv = false,
                playerHls = false
            )
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null, authLoading = true) }
            authService.signIn(email, password)
                .onSuccess {
                    finishOnboarding()
                    _state.update { it.copy(profileMessage = "Signed in") }
                    viewModelScope.launch { syncIfLoggedIn() }
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
                    finishOnboarding()
                    _state.update { it.copy(profileMessage = "Account created") }
                    viewModelScope.launch { syncIfLoggedIn() }
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
                "Sign-in is not set up on this build. Try guest mode or check your account settings."
            msg.contains("API key", ignoreCase = true) -> "Sign-in configuration error. Try guest mode."
            msg.contains("network", ignoreCase = true) -> "Network error — check your connection and try again."
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
            _state.update { it.copy(profileMessage = "Library synced") }
        }
    }

    private suspend fun syncIfLoggedIn(): Boolean {
        if (!_state.value.isLoggedIn) return false
        val s = _state.value
        val (profiles, activeId) = profileStore.snapshot()
        var ok = true
        syncService.pushAccountMeta(activeId, profiles).onFailure { ok = false }
        for (profile in profiles) {
            syncService.pushProfileData(
                profileId = profile.id,
                displayName = profile.name,
                email = s.userEmail,
                favorites = favoritesStore.readOnce(profile.id),
                continueWatching = cwStore.readOnce(profile.id),
                playlists = playlistStore.readOnce(profile.id)
            ).onFailure { ok = false }
        }
        return ok
    }

    private fun restoreProfileAvatarsFromCloud(profiles: List<ViewerProfile>) {
        val app = getApplication<android.app.Application>()
        profiles.forEach { profile ->
            profile.avatarUri?.takeIf { it.startsWith("http", ignoreCase = true) }?.let { return@forEach }
            val b64 = profile.avatarBase64 ?: return@forEach
            try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                val dir = java.io.File(app.filesDir, "profile_avatars").apply { mkdirs() }
                java.io.File(dir, "${profile.id}.jpg").writeBytes(bytes)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun pullCloudSafe() {
        if (!_state.value.isLoggedIn) return
        val account = syncService.pullAccount() ?: return

        profileStore.importFromCloud(account.profiles, account.activeProfileId)
        restoreProfileAvatarsFromCloud(account.profiles)

        for ((profileId, cloud) in account.profileData) {
            if (cloud.favorites.isNotEmpty()) favoritesStore.replaceAll(profileId, cloud.favorites)
            if (cloud.continueWatching.isNotEmpty()) cwStore.replaceAll(profileId, cloud.continueWatching)
            if (cloud.playlists.isNotEmpty()) playlistStore.replaceAll(profileId, cloud.playlists)
        }

        val activeCloud = account.profileData[account.activeProfileId]
        _state.update {
            it.copy(
                profileMessage = "Account data loaded",
                userDisplayName = activeCloud?.profile?.displayName?.ifBlank { null }
                    ?: it.userDisplayName
            )
        }
    }
}
