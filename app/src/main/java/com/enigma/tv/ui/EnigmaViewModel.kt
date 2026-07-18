package com.enigma.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.ContinueWatchingStore
import com.enigma.tv.data.ContentType
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
import com.enigma.tv.data.MediaTrailerUi
import com.enigma.tv.data.VideoResults
import com.enigma.tv.data.pickTrailers
import com.enigma.tv.data.usContentRating
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.Playlist
import com.enigma.tv.data.PlaylistStore
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.ProfileStore
import com.enigma.tv.data.SearchResults
import com.enigma.tv.data.SearchSuggestion
import com.enigma.tv.data.StreamSources
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.data.TmdbRepository
import com.enigma.tv.data.TvItem
import com.enigma.tv.data.UserSessionStore
import com.enigma.tv.EnigmaApplication
import androidx.media3.exoplayer.offline.Download
import com.enigma.tv.data.canStream
import com.enigma.tv.data.comingSoonLabel
import com.enigma.tv.data.firebase.FirebaseAuthService
import com.enigma.tv.data.firebase.FirebaseSyncService
import com.enigma.tv.data.formatRuntime
import com.enigma.tv.data.ImgurUploadService
import com.enigma.tv.data.ProfileImageStorage
import com.enigma.tv.update.UpdateChecker
import com.enigma.tv.update.UpdateInfo
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
    SEARCH("Search"),
    LIVE("Live TV"),
    PLAYLISTS("Playlists"),
    CONTINUE("Continue Watching"),
    LISTS("My Lists"),
    DOWNLOADS("Downloads"),
    PROFILE("Account"),
    DEV_TEST("Developer Testing")
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
    val isOffline: Boolean = false,
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
    val playerLoadingMessage: String? = null,
    val playerTitle: String = "",
    val playerUrl: String = "",
    val playerLogoUrl: String? = null,
    val playerResolveToken: Int = 0,
    val playerAccentMovie: Boolean = true,
    val playerAccentColor: Int? = null,
    val playerLiveTv: Boolean = false,
    val playerStreamFailed: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
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
    val showProfilePicker: Boolean = false,
    /** Non-null while opening a profile — keeps picker mounted with a loading overlay (avoids TV tear-down crash). */
    val openingProfileId: String? = null,
    val searchSuggestions: List<SearchSuggestion> = emptyList(),
    val playerLiveHint: String? = null,
    val playerLiveEventStartMs: Long = 0L,
    val playerStreamPlaying: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val playerSubtitleUrl: String? = null
)

class EnigmaViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TmdbRepository()
    private val cwStore = ContinueWatchingStore(application)
    private val playlistStore = PlaylistStore(application)
    private val sessionStore = UserSessionStore(application)
    private val profileStore = ProfileStore(application)
    private val authService by lazy { FirebaseAuthService() }
    private val syncService by lazy { FirebaseSyncService() }
    private val subtitleCache = mutableMapOf<String, String>()

    private val _state = MutableStateFlow(EnigmaUiState())
    val state: StateFlow<EnigmaUiState> = _state.asStateFlow()
    private val iptvRepo = IptvRepository()
    private val streamedRepo = StreamedRepository()
    private val liveChannelStore = LiveChannelFavoritesStore(application)
    private val imageLoader = ImageLoader(application)

    private var syncJob: Job? = null
    private var tvNavJob: Job? = null
    private var homeLoadJob: Job? = null
    private var persistCwJob: Job? = null
    private var suggestJob: Job? = null
    @Volatile
    private var profileSelectionInProgress = false
    private var postProfileSyncJob: Job? = null

    private fun activeProfileId(): String = _state.value.activeProfileId.ifBlank { "default" }

    private fun cloudSyncBlocked(): Boolean =
        profileSelectionInProgress ||
            !_state.value.openingProfileId.isNullOrBlank()

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
            val (profiles, activeId) = profileStore.snapshot()
            val metaOk = syncService.pushAccountMeta(activeId, profiles).isSuccess
            val libOk = syncIfLoggedIn()
            val ok = metaOk && libOk
            _state.update {
                it.copy(
                    profileMessage = if (ok) {
                        "Saved ${profiles.size} profile(s) to cloud"
                    } else {
                        "Could not sync — check connection and Firebase sign-in"
                    },
                    profileError = if (ok) null else it.profileError
                )
            }
        }
    }

    private fun schedulePersistContinueWatching() {
        persistCwJob?.cancel()
        persistCwJob = viewModelScope.launch {
            delay(400)
            persistContinueWatching()
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
        val cm = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val request = android.net.NetworkRequest.Builder().build()
        cm.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                _state.update { it.copy(isOffline = false) }
            }
            override fun onLost(network: android.net.Network) {
                _state.update { it.copy(isOffline = true) }
            }
        })
        if (cm.activeNetwork == null) {
            _state.update { it.copy(isOffline = true) }
        }

        viewModelScope.launch {
            profileStore.activeProfileId.flatMapLatest { profileId ->
                combine(
                    playlistStore.watch(profileId),
                    cwStore.watch(profileId)
                ) { p, c -> Pair(p, c) }
            }.collect { (lists, cw) ->
                _state.update { it.copy(playlists = lists, continueWatching = cw) }
                scheduleCloudSync()
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            authService.authState.collect { user ->
                _state.update {
                    it.copy(
                        isLoggedIn = user != null,
                        userEmail = user?.email.orEmpty(),
                        userDisplayName = user?.displayName ?: "Guest"
                    )
                }
                if (user != null) {
                    viewModelScope.launch {
                        delay(1500)
                        if (!cloudSyncBlocked()) {
                            pullCloudSafe()
                            syncIfLoggedIn()
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            try {
                // Wait a few seconds for the user to pass the Profile Picker screen
                delay(3000)
                val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
                val version = pInfo.versionName ?: ""
                val update = UpdateChecker.checkForUpdate(version)
                if (update != null && update.hasUpdate) {
                    _state.update { it.copy(updateInfo = update) }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        viewModelScope.launch {
            profileStore.ensureDefaultProfile()
            val done = sessionStore.isOnboardingComplete()
            // Unblock the UI immediately — don't wait for slow DataStore reads on Firestick.
            // Profiles load reactively via the collect() above and will populate shortly.
            _state.update {
                it.copy(
                    sessionReady = true,
                    showAuthGate = !done,
                    showSplash = false,
                    showProfilePicker = done,
                    contentLoading = false
                )
            }
            // Now do a background read to fill in the real profile values
            val profiles = profileStore.profiles.first()
            val activeId = profileStore.activeProfileId.first()
            _state.update {
                it.copy(
                    profiles = profiles,
                    activeProfileId = activeId.ifBlank { it.activeProfileId }
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearAuthError() = _state.update { it.copy(profileError = null) }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileStore.setActive(profileId)
            pullCloudSafe()
            syncIfLoggedIn()
        }
    }

    fun selectProfileAndContinue(profileId: String) {
        if (profileId.isBlank() || profileSelectionInProgress) return
        profileSelectionInProgress = true
        postProfileSyncJob?.cancel()
        viewModelScope.launch {
            try {
                profileStore.setActive(profileId)
                val (profiles, _) = profileStore.snapshot()
                val resolvedId = profiles.find { it.id == profileId }?.id
                    ?: profiles.firstOrNull()?.id
                    ?: profileStore.ensureDefaultProfile().id
                if (resolvedId != profileId) profileStore.setActive(resolvedId)
                _state.update {
                    it.copy(
                        activeProfileId = resolvedId,
                        openingProfileId = resolvedId,
                        showProfilePicker = true,
                        // Keep existing homeRows: old content stays visible behind the picker
                        // overlay while new content loads. This prevents bootstrapLoading
                        // from firing and eliminates the TV focus-tree destruction crash.
                        error = null,
                        profileError = null
                    )
                }
                loadHome(closeProfileGate = true)
                schedulePostProfileCloudSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        contentLoading = false,
                        openingProfileId = null,
                        error = "Could not open that profile. Try again.",
                        showProfilePicker = true,
                        profileError = t.message?.take(120)
                    )
                }
            } finally {
                profileSelectionInProgress = false
            }
        }
    }

    private fun schedulePostProfileCloudSync() {
        postProfileSyncJob?.cancel()
        postProfileSyncJob = viewModelScope.launch {
            delay(6000)
            if (cloudSyncBlocked()) return@launch
            syncIfLoggedIn()
            pullCloudSafe()
        }
    }

    fun dismissProfilePicker() {
        _state.update {
            it.copy(
                showProfilePicker = false,
                openingProfileId = null,
                contentLoading = it.homeRows.isEmpty(),
                homeRows = if (it.homeRows.isEmpty()) emptyList() else it.homeRows
            )
        }
        if (_state.value.homeRows.isEmpty()) loadHome()
    }

    fun showAuthGateFromProfile() {
        _state.update { it.copy(showAuthGate = true, showProfilePicker = false) }
    }

    fun showProfilePickerScreen() {
        postProfileSyncJob?.cancel()
        _state.update {
            it.copy(
                showProfilePicker = true,
                openingProfileId = null,
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

    fun refreshProfilesFromCloud() {
        if (!_state.value.isLoggedIn || profileSelectionInProgress) return
        viewModelScope.launch {
            // Pull FIRST so cloud profiles aren't overwritten by stale local data.
            // Only push back after we've merged, so we sync local-only items like
            // continue-watching, but never stomp cloud-only profiles.
            pullCloudSafe()
            syncIfLoggedIn()
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
            val cloudUrl = imgurUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
            profileStore.setProfileAvatarData(
                profileId,
                avatarUri = cloudUrl ?: uri,
                avatarBase64 = if (cloudUrl != null) null else base64
            )
            _state.update {
                it.copy(
                    profileMessage = when {
                        cloudUrl != null -> "Photo uploaded — syncing to cloud"
                        else -> "Photo saved on device; Imgur upload failed"
                    }
                )
            }
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

    fun loadHome(closeProfileGate: Boolean = false) {
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            _state.update { it.copy(contentLoading = it.homeRows.isEmpty(), error = null, searchResults = null) }
            try {
                val rows = repo.buildHomeRows()
                val isTV = getApplication<android.app.Application>().packageManager
                    .hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
                val shouldCloseGate = closeProfileGate || _state.value.openingProfileId != null

                // Step 1: deliver new rows and clear the "OPENING" spinner.
                // The picker overlay itself stays up a moment longer so it remains in the
                // Compose tree and TV focus nodes stay valid.
                _state.update {
                    it.copy(
                        homeRows = rows,
                        contentLoading = false,
                        openingProfileId = null,
                        error = if (rows.isEmpty()) "Could not load EnigmaTV content. Check connection and retry." else null
                    )
                }

                if (shouldCloseGate) {
                    // Step 2: On TV wait ~2 frames so main-content focus nodes are
                    // established before the picker overlay is removed from composition.
                    if (isTV) delay(120)
                    _state.update { it.copy(showProfilePicker = false) }
                }
                if (rows.isNotEmpty()) {
                    prefetchHomePosters(rows)
                    // Pre-warm Live TV in background so tapping Live is instant
                    if (_state.value.liveTv.channels.isEmpty() && !_state.value.liveTv.loading) {
                        viewModelScope.launch { loadLiveTv() }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update {
                    val closeGate = closeProfileGate || it.openingProfileId != null
                    it.copy(
                        contentLoading = false,
                        showProfilePicker = if (closeGate) false else it.showProfilePicker,
                        openingProfileId = if (closeGate) null else it.openingProfileId,
                        error = "Could not load EnigmaTV content. Check connection and retry."
                    )
                }
            }
        }
    }

    private suspend fun finishOnboarding() {
        sessionStore.setOnboardingComplete()
        val profiles = profileStore.profiles.first().ifEmpty {
            listOf(profileStore.ensureDefaultProfile())
        }
        val activeId = profileStore.activeProfileId.first()

        // Step 1: hide the auth gate — the main app tree (PermanentNavigationDrawer +
        // all drawer items) begins composing and registers its TV focus nodes.
        _state.update {
            it.copy(
                showAuthGate = false,
                showProfilePicker = true,
                authLoading = false,
                profileError = null,
                contentLoading = false,
                profiles = profiles,
                activeProfileId = activeId.ifBlank { profiles.firstOrNull()?.id ?: "default" }
            )
        }

        // Step 2: on TV the PermanentNavigationDrawer needs ~2 frames to fully lay out
        // its focusable items before we overlay the profile picker on top. Without this
        // delay the Leanback focus tree initialises two large subtrees simultaneously
        // and throws an IllegalStateException (crash). 150 ms is safely > 2 frames at
        // 60 fps. On phone/tablet the same delay is harmless.
        kotlinx.coroutines.delay(150)

        _state.update { it.copy(showProfilePicker = true) }
        schedulePostProfileCloudSync()
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

    fun onSearchQueryChanged(query: String) {
        suggestJob?.cancel()
        if (query.trim() == "*xyz*") {
            _state.update { it.copy(section = NavSection.DEV_TEST, searchSuggestions = emptyList()) }
            return
        }
        if (query.trim().length < 2) {
            _state.update { it.copy(searchSuggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(320)
            val suggestions = repo.searchSuggestions(query)
            _state.update { it.copy(searchSuggestions = suggestions) }
        }
    }

    fun clearSearchSuggestions() {
        suggestJob?.cancel()
        _state.update { it.copy(searchSuggestions = emptyList()) }
    }

    fun clearSearch() {
        _state.update { it.copy(searchResults = null) }
    }

    fun pickSearchSuggestion(suggestion: SearchSuggestion) {
        clearSearchSuggestions()
        when (suggestion.type) {
            ContentType.MOVIE -> openMovieDetail(MovieItem(suggestion.id, suggestion.title))
            ContentType.TV -> openTvDetail(TvItem(suggestion.id, suggestion.title))
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        clearSearchSuggestions()
        viewModelScope.launch {
            _state.update { it.copy(contentLoading = true, error = null) }
            try {
                coroutineScope {
                    val movies = async { repo.searchMovies(query) }
                    val tv = async { repo.searchTv(query) }
                    _state.update {
                        val stayInSection = if (it.section == NavSection.SEARCH) NavSection.SEARCH else NavSection.HOME
                        it.copy(contentLoading = false, searchResults = SearchResults(movies.await(), tv.await()), section = stayInSection)
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
                val cw = _state.value.continueWatching.find { it.id == id && it.type == type }
                val detail = when (type) {
                    ContentType.MOVIE -> buildMovieDetail(id, false, cw)
                    ContentType.TV -> buildTvDetail(id, false, cw)
                }
                _state.update { it.copy(detailLoading = false, detail = detail) }
            } catch (e: Exception) {
                _state.update { it.copy(detailLoading = false, error = "Could not load details", showDetail = false) }
            }
        }
    }

    private suspend fun buildMovieDetail(id: Int, isFavorite: Boolean, cw: ContinueWatchingEntry?): MediaDetailUi {
        val d = repo.movieDetail(id)
        val movie = MovieItem(id, d.title, d.posterPath, d.backdropPath, d.releaseDate, d.voteAverage, d.overview)
        val rating = formatRating(d.voteAverage, d.voteCount)
        val imdbId = d.externalIds?.imdbId
        val omdb = imdbId?.let { repo.omdbRatings(it) }
        val rtRating = omdb?.ratings?.find { it.source.contains("Rotten Tomatoes", ignoreCase = true) }?.value
        
        return MediaDetailUi(
            type = ContentType.MOVIE,
            id = id,
            title = d.title,
            overview = d.overview.orEmpty(),
            posterUrl = movie.posterUrl,
            backdropUrl = movie.backdropUrl,
            metaLine = buildMetaLine(d.voteAverage, formatRuntime(d.runtime) ?: "Movie", movie.year),
            releaseLabel = movie.comingSoonLabel(),
            ratingScore = rating.first,
            ratingVotes = rating.second,
            imdbRating = omdb?.imdbRating,
            rottenTomatoesRating = rtRating,
            contentRating = d.releaseDates.usContentRating(),
            genresText = d.genres.joinToString(" · ") { it.name },
            cast = d.credits?.cast?.take(15) ?: emptyList(),
            trailers = mapTrailers(d.videos),
            isPlayable = movie.canStream(),
            isFavorite = isFavorite,
            resumePositionMs = cw?.positionMs ?: 0L,
            isInContinueWatching = cw != null
        )
    }

    private suspend fun buildTvDetail(id: Int, isFavorite: Boolean, cw: ContinueWatchingEntry?): MediaDetailUi {
        val d = repo.tvDetail(id)
        val show = TvItem(id, d.name, posterPath = d.posterPath, backdropPath = d.backdropPath, firstAirDate = d.firstAirDate, voteAverage = d.voteAverage, overview = d.overview)
        val seasons = d.seasons.filter { it.seasonNumber > 0 }.map { it.seasonNumber }.ifEmpty { listOf(1) }
        val season = seasons.first()
        val eps = runCatching { repo.tvSeason(id, season) }.getOrDefault(emptyList())
        val rating = formatRating(d.voteAverage, d.voteCount)
        val imdbId = d.externalIds?.imdbId
        val omdb = imdbId?.let { repo.omdbRatings(it) }
        val rtRating = omdb?.ratings?.find { it.source.contains("Rotten Tomatoes", ignoreCase = true) }?.value
        
        return MediaDetailUi(
            type = ContentType.TV,
            id = id,
            title = d.name,
            overview = d.overview.orEmpty(),
            posterUrl = show.posterUrl,
            backdropUrl = show.backdropUrl,
            metaLine = buildMetaLine(d.voteAverage, "${d.numberOfSeasons} seasons", show.year),
            releaseLabel = show.comingSoonLabel(),
            ratingScore = rating.first,
            ratingVotes = rating.second,
            imdbRating = omdb?.imdbRating,
            rottenTomatoesRating = rtRating,
            contentRating = d.contentRatings.usContentRating(),
            genresText = d.genres.joinToString(" · ") { it.name },
            cast = d.credits?.cast?.take(15) ?: emptyList(),
            trailers = mapTrailers(d.videos),
            isPlayable = show.canStream(),
            seasons = seasons,
            episodes = eps,
            selectedSeason = cw?.season ?: season,
            selectedEpisode = cw?.episode ?: eps.firstOrNull()?.episodeNumber ?: 1,
            isFavorite = isFavorite,
            resumePositionMs = cw?.positionMs ?: 0L,
            resumeSeason = cw?.season,
            resumeEpisode = cw?.episode,
            isInContinueWatching = cw != null
        )
    }

    private fun formatRating(voteAverage: Double, voteCount: Int): Pair<String, String?> {
        val score = if (voteAverage > 0) String.format("%.1f", voteAverage) else "—"
        val votes = if (voteCount > 0) {
            when {
                voteCount >= 1_000_000 -> String.format("%.1fM ratings", voteCount / 1_000_000.0)
                voteCount >= 1_000 -> String.format("%,d ratings", voteCount)
                else -> "$voteCount ratings"
            }
        } else null
        return score to votes
    }

    private fun buildMetaLine(voteAverage: Double, extra: String, year: String): String {
        val star = if (voteAverage > 0) "★ ${"%.1f".format(voteAverage)}" else ""
        return listOf(star, year.takeIf { it.isNotBlank() && it != "?" }, extra)
            .filter { it.isNotNullOrBlank() }
            .joinToString(" · ")
    }

    private fun String?.isNotNullOrBlank() = !isNullOrBlank()

    private fun mapTrailers(videos: VideoResults?): List<MediaTrailerUi> =
        videos.pickTrailers().mapNotNull { video ->
            val url = video.youtubeWatchUrl ?: return@mapNotNull null
            MediaTrailerUi(
                name = video.name,
                youtubeUrl = url,
                thumbnailUrl = video.youtubeThumbnailUrl,
                official = video.official
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

    fun playFromDetail(restart: Boolean = false) {
        val d = _state.value.detail ?: return
        closeDetail()
        when (d.type) {
            ContentType.MOVIE -> {
                playMovie(MovieItem(d.id, d.title), d.posterUrl, startPositionMs = if (restart) 0L else d.resumePositionMs)
            }
            ContentType.TV -> {
                val pos = if (restart) 0L else if (d.selectedSeason == d.resumeSeason && d.selectedEpisode == d.resumeEpisode) d.resumePositionMs else 0L
                selectShow(d.id, d.title, d.selectedSeason, d.selectedEpisode, startPositionMs = pos)
            }
        }
    }


    private fun prefetchHomePosters(rows: List<HomeRow>) {
        val urls = rows.flatMap { row ->
            when (row) {
                is HomeRow.Movies -> row.items.mapNotNull { it.posterUrl }
                is HomeRow.TvShows -> row.items.mapNotNull { it.posterUrl }
            }
        }.distinct().take(
            if (getApplication<android.app.Application>().packageManager.hasSystemFeature(
                    android.content.pm.PackageManager.FEATURE_LEANBACK
                )
            ) 12 else 36
        )
        urls.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(getApplication())
                    .data(url)
                    .size(342, 513)
                    .build()
            )
        }
    }

    fun playMovie(movie: MovieItem, posterOverride: String? = null, startPositionMs: Long = 0L) {
        val poster = posterOverride?.takeIf { it.isNotBlank() } ?: movie.posterUrl
        val (name, url) = StreamSources.movieUrl(0, movie.id)
        _state.update {
            it.copy(
                playerVisible = true,
                playerLiveHint = null,
                playerLiveEventStartMs = 0L,
                playerLoading = true,
                playerHls = false,
                playerTitle = movie.title,
                playerUrl = url,
                playerLogoUrl = poster,
                playerAccentMovie = true,
                playerLiveTv = false,
                playingType = ContentType.MOVIE,
                currentMovieId = movie.id,
                currentShowId = null,
                sourceIndex = 0,
                playbackPositionMs = startPositionMs.coerceAtLeast(0L),
                sourceLabel = "Enigma Player · $name (1/${StreamSources.movieSources.size})"
            )
        }
        viewModelScope.launch {
            val resumeMs = startPositionMs.coerceAtLeast(0L)
            cwStore.addOrUpdate(
                activeProfileId(),
                ContinueWatchingEntry(
                    id = movie.id,
                    name = movie.title,
                    poster = poster ?: "",
                    season = 0,
                    episode = 0,
                    type = ContentType.MOVIE,
                    positionMs = resumeMs
                )
            )
            syncIfLoggedIn()
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
                val now = System.currentTimeMillis()
                val isLiveNow = match.dateMs in (now - 4 * 3_600_000L)..(now + 45 * 60_000L)
                when {
                    streams.isEmpty() -> _state.update { it.copy(error = "No streams available for this event") }
                    else -> {
                        _state.update {
                            it.copy(
                                liveStreamPicker = streams,
                                playerTitle = match.title,
                                playerLiveEventStartMs = if (isLiveNow) 0L else match.dateMs,
                                playerLiveHint = null,
                                playerStreamPlaying = false
                            )
                        }
                        playLiveEmbed(
                            match.title,
                            streams.first().embedUrl,
                            streams.first().label,
                            pickerIndex = 0
                        )
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
        playLiveEmbed(title, link.embedUrl, link.label, pickerIndex = index)
    }

    fun dismissLiveStreamPicker() {
        _state.update { it.copy(showLiveStreamPicker = false, liveStreamPicker = emptyList()) }
    }

    private fun isPreLiveEvent(): Boolean {
        val start = _state.value.playerLiveEventStartMs
        return start > System.currentTimeMillis() + 60_000L
    }

    private fun playLiveEmbed(title: String, embedUrl: String, label: String, pickerIndex: Int? = null) {
        val idx = pickerIndex ?: _state.value.sourceIndex
        val pickerSize = _state.value.liveStreamPicker.size.coerceAtLeast(1)

        // Block player from opening if the event hasn't started yet.
        // Prevents ugly HTML/JSON error pages from showing in WebView.
        if (isPreLiveEvent()) {
            val delta = _state.value.playerLiveEventStartMs - System.currentTimeMillis()
            val mins = (delta / 60_000L).coerceAtLeast(1)
            val hours = mins / 60
            val remMins = mins % 60
            val timeLabel = if (hours > 0) "${hours}h ${remMins}m" else "${mins}m"
            _state.update {
                it.copy(
                    playerVisible = true,
                    playerHls = false,
                    playerLiveTv = false,
                    playerLoading = false,
                    playerStreamFailed = true,
                    playerLiveHint = "Broadcast begins in $timeLabel",
                    playerTitle = title,
                    sourceLabel = "Live · $label",
                    error = null
                )
            }
            return
        }

        // Build the best direct embed player URL without server-side resolution.
        // Server-side fetching of streamed.pk embed pages fails because they require
        // JS execution in a real browser to deliver the stream. Going directly to the
        // embed player URL in WebView is the correct approach.
        val directCandidates = StreamedRepository.embedCandidates(embedUrl)
            .filter { it.startsWith("http", ignoreCase = true) && !LiveEmbedResolver.isUnplayableUrl(it) }
        val playUrl = directCandidates.firstOrNull()
            ?: embedUrl.trim().takeIf { it.startsWith("http") && !LiveEmbedResolver.isUnplayableUrl(it) }

        if (playUrl.isNullOrBlank()) {
            _state.update {
                it.copy(
                    playerVisible = true,
                    playerLoading = false,
                    playerStreamFailed = true,
                    playerLiveHint = "No playable URL found. Try Next Server.",
                    error = null
                )
            }
            return
        }

        _state.update {
            it.copy(
                playerVisible = true,
                playerHls = false,
                playerLiveTv = true,
                playerLoading = true,
                playerStreamFailed = false,
                playerLiveHint = null,
                playerStreamPlaying = false,
                playerTitle = title,
                playerUrl = playUrl,
                playerAccentMovie = false,
                playingType = null,
                sourceLabel = "Live · $label (${idx + 1}/$pickerSize)",
                showLiveStreamPicker = false,
                sourceIndex = idx,
                playerResolveToken = it.playerResolveToken + 1,
                error = null
            )
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



    fun setPlayerLoadingMessage(message: String?) {
        _state.update { it.copy(playerLoadingMessage = message) }
    }
    
    fun setPlayerAccentColor(colorInt: Int) {
        _state.update { it.copy(playerAccentColor = colorInt) }
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

    // Subtitle Management
    private fun buildSubtitleKey(): String? {
        val st = _state.value
        val type = st.playingType ?: return null
        val tmdbId = st.currentMovieId ?: st.currentShowId ?: return null
        return if (type == ContentType.TV) "${tmdbId}_${st.selectedSeason}_${st.selectedEpisode}" else tmdbId.toString()
    }

    fun cacheSubtitle(url: String) {
        val key = buildSubtitleKey() ?: return
        subtitleCache[key] = url
        _state.update { it.copy(playerSubtitleUrl = url) }
    }

    fun getCachedSubtitle(): String? {
        return buildSubtitleKey()?.let { subtitleCache[it] }
    }

    /** Open detail screen for a FavoriteItem from a playlist. */
    fun playPlaylistItem(item: FavoriteItem) {
        loadDetail(item.type, item.id, item.title, item.poster.ifBlank { null })
    }

    fun openDetailFromContinue(entry: ContinueWatchingEntry) {
        when (entry.type) {
            ContentType.MOVIE -> openMovieDetail(MovieItem(entry.id, entry.name))
            ContentType.TV -> openTvDetail(TvItem(entry.id, entry.name))
        }
    }

    fun resumeContinue(entry: ContinueWatchingEntry) {
        when (entry.type) {
            ContentType.MOVIE -> playMovie(
                MovieItem(entry.id, entry.name),
                entry.poster,
                startPositionMs = entry.positionMs
            )
            ContentType.TV -> selectShow(
                entry.id,
                entry.name,
                resumeSeason = entry.season,
                resumeEpisode = entry.episode,
                startPositionMs = entry.positionMs
            )
        }
    }

    fun removeFromContinue(id: Int, type: ContentType) {
        viewModelScope.launch {
            cwStore.removeEntry(activeProfileId(), id, type)
            _state.update { st ->
                st.copy(continueWatching = st.continueWatching.filter {
                    it.id != id || it.type != type
                })
            }
            syncIfLoggedIn()
        }
    }

    fun removeFromContinue(entry: ContinueWatchingEntry) {
        removeFromContinue(entry.id, entry.type)
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

    fun removeFromPlaylist(playlistId: String, item: FavoriteItem) {
        viewModelScope.launch {
            playlistStore.removeItem(activeProfileId(), playlistId, item)
            syncIfLoggedIn()
        }
    }

    fun playPlaylistFrom(playlistId: String, startIndex: Int = 0) {
        val playlist = _state.value.playlists.find { it.id == playlistId } ?: return
        if (playlist.items.isEmpty()) return
        val item = playlist.items.getOrNull(startIndex) ?: return
        _state.update { it.copy(selectedPlaylistId = playlistId) }
        when (item.type) {
            ContentType.MOVIE -> openMovieDetail(MovieItem(item.id, item.title))
            ContentType.TV -> openTvDetail(TvItem(item.id, item.title))
        }
    }

    fun getPlaylistBingeNext(playlistId: String, currentItemId: Int, currentItemType: ContentType): String? {
        val playlist = _state.value.playlists.find { it.id == playlistId } ?: return null
        val idx = playlist.items.indexOfFirst { it.id == currentItemId && it.type == currentItemType }
        val next = playlist.items.getOrNull(idx + 1) ?: return null
        return next.title
    }

    // Downloads – 10 GB storage limit
    private val maxDownloadBytes = 10L * 1024 * 1024 * 1024 // 10 GB

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun enqueueDownload(context: android.content.Context, title: String, url: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val downloads = getDownloads(context)
            val totalBytes = downloads.sumOf { it.bytesDownloaded }
            if (totalBytes >= maxDownloadBytes) {
                _state.update { it.copy(error = "Storage limit reached (10 GB). Delete downloads to free space.") }
                return@launch
            }
            val uri = android.net.Uri.parse(url)
            val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder(url, uri)
                .setData(title.toByteArray(Charsets.UTF_8))
                .build()
            androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                context,
                com.enigma.tv.EnigmaDownloadService::class.java,
                request,
                false
            )
        }
    }

    fun downloadDetailItem(context: android.content.Context) {
        val detail = _state.value.detail ?: return
        val url = if (detail.type == ContentType.MOVIE) {
            com.enigma.tv.data.StreamSources.movieUrl(_state.value.sourceIndex, detail.id).second
        } else {
            com.enigma.tv.data.StreamSources.tvUrl(_state.value.sourceIndex, detail.id, detail.selectedSeason, detail.selectedEpisode).second
        }
        
        viewModelScope.launch {
            try {
                _state.update { it.copy(error = "Resolving stream for download...") }
                val extractor = com.enigma.tv.data.StreamExtractor(context)
                val result = extractor.extractStreamUrl(url)
                if (result != null) {
                    enqueueDownload(context, detail.title, result.url)
                    _state.update { it.copy(error = "Download started.") }
                } else {
                    _state.update { it.copy(error = "Could not resolve stream for download.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to start download: ${e.message}") }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun removeDownload(context: android.content.Context, downloadId: String) {
        androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
            context,
            com.enigma.tv.EnigmaDownloadService::class.java,
            downloadId,
            false
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getDownloads(context: android.content.Context): List<androidx.media3.exoplayer.offline.Download> {
        val dm = EnigmaApplication.getDownloadManager(context)
        val list = mutableListOf<androidx.media3.exoplayer.offline.Download>()
        val cursor = dm.downloadIndex.getDownloads()
        try {
            while (cursor.moveToNext()) {
                list.add(cursor.download)
            }
        } catch (e: Exception) {
        } finally {
            cursor.close()
        }
        return list
    }

    fun selectShow(
        id: Int,
        name: String,
        resumeSeason: Int? = null,
        resumeEpisode: Int? = null,
        startPositionMs: Long = 0L
    ) {
        viewModelScope.launch {
            val startSeason = resumeSeason ?: 1
            val startEpisode = resumeEpisode ?: 1
            _state.update {
                it.copy(
                    playerVisible = true,
                    playerLoading = true,
                    playerLiveHint = null,
                    playerLiveEventStartMs = 0L,
                    playerHls = false,
                    playerTitle = name,
                    playerAccentMovie = false,
                    playerLiveTv = false,
                    playingType = ContentType.TV,
                    currentShowId = id,
                    currentMovieId = null,
                    sourceIndex = 0,
                    selectedSeason = startSeason,
                    selectedEpisode = startEpisode,
                    playbackPositionMs = startPositionMs.coerceAtLeast(0L)
                )
            }
            try {
                val detail = repo.tvDetail(id)
                val seasons = detail.seasons.filter { it.seasonNumber > 0 }.map { it.seasonNumber }
                val posterUrl = detail.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
                cwStore.addOrUpdate(
                    activeProfileId(),
                    ContinueWatchingEntry(
                        id = id,
                        name = name,
                        poster = posterUrl ?: "",
                        season = startSeason,
                        episode = startEpisode,
                        type = ContentType.TV,
                        positionMs = startPositionMs.coerceAtLeast(0L)
                    )
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

    fun getBingeNextLabel(): String? {
        val st = _state.value
        if (st.playingType == ContentType.TV) {
            val currEpIdx = st.episodes.indexOfFirst { it.first == st.selectedEpisode }
            if (currEpIdx >= 0 && currEpIdx < st.episodes.size - 1) {
                val nextEp = st.episodes[currEpIdx + 1]
                return "S${st.selectedSeason}E${nextEp.first} - ${nextEp.second}"
            } else if (st.seasons.contains(st.selectedSeason + 1)) {
                return "Season ${st.selectedSeason + 1}"
            }
        }
        // TODO: Playlist support
        return null
    }

    fun playBingeNext() {
        val st = _state.value
        if (st.playingType == ContentType.TV) {
            val currEpIdx = st.episodes.indexOfFirst { it.first == st.selectedEpisode }
            if (currEpIdx >= 0 && currEpIdx < st.episodes.size - 1) {
                val nextEp = st.episodes[currEpIdx + 1]
                onEpisodeChange(nextEp.first)
            } else if (st.seasons.contains(st.selectedSeason + 1)) {
                viewModelScope.launch {
                    val newEpisodes = repo.tvSeason(st.currentShowId!!, st.selectedSeason + 1).map { it.episodeNumber to it.name }.sortedBy { it.first }
                    if (newEpisodes.isNotEmpty()) {
                        _state.update { it.copy(selectedSeason = st.selectedSeason + 1, episodes = newEpisodes, selectedEpisode = newEpisodes.first().first) }
                        playCurrentEpisode()
                    }
                }
            }
        }
    }

    fun onSeasonChange(seasonNumber: Int) {
        val showId = _state.value.currentShowId ?: return
        val keepEpisode = _state.value.selectedEpisode
        tvNavJob?.cancel()
        tvNavJob = viewModelScope.launch {
            _state.update { it.copy(playerLoading = true) }
            loadEpisodesInternal(showId, seasonNumber, keepEpisode, play = true)
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

    fun onPlaybackPositionMs(positionMs: Long) {
        val ms = positionMs.coerceAtLeast(0L)
        _state.update { it.copy(playbackPositionMs = ms) }
        if (ms >= 2_000L) schedulePersistContinueWatching()
    }

    fun onPlaybackDurationMs(durationMs: Long) {
        if (durationMs > 0L) _state.update { it.copy(playbackDurationMs = durationMs) }
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
                        positionMs = s.playbackPositionMs,
                        durationMs = s.playbackDurationMs
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
                        type = ContentType.TV,
                        positionMs = s.playbackPositionMs,
                        durationMs = s.playbackDurationMs
                    )
                )
            }
            else -> return
        }
        syncActiveProfileLibrary()
    }

    private fun syncActiveProfileLibrary() {
        if (!_state.value.isLoggedIn) return
        viewModelScope.launch {
            val s = _state.value
            val profileId = activeProfileId()
            val profile = s.profiles.find { it.id == profileId }
            syncService.pushProfileData(
                profileId = profileId,
                displayName = profile?.name ?: "Profile",
                email = s.userEmail,
                continueWatching = cwStore.readOnce(profileId),
                playlists = playlistStore.readOnce(profileId)
            )
            scheduleCloudSync()
        }
    }

    fun nextSource() {
        val s = _state.value
        when (s.playingType) {
            ContentType.MOVIE -> {
                val id = s.currentMovieId ?: return
                val next = s.sourceIndex + 1
                // If we've cycled through all sources, show error instead of looping
                if (next >= StreamSources.movieSources.size) {
                    _state.update {
                        it.copy(
                            playerLoading = false,
                            playerStreamFailed = true,
                            playerLiveHint = "No playable sources found for this title."
                        )
                    }
                    return
                }
                val (name, url) = StreamSources.movieUrl(next, id)
                val resumeMs = s.playbackPositionMs
                _state.update {
                    it.copy(
                        playerLoading = true,
                        sourceIndex = next,
                        playerUrl = url,
                        playbackPositionMs = resumeMs,
                        playerResolveToken = it.playerResolveToken + 1,
                        sourceLabel = "Enigma Player · $name (${next + 1}/${StreamSources.movieSources.size})"
                    )
                }
            }
            ContentType.TV -> {
                val nextIdx = s.sourceIndex + 1
                if (nextIdx >= StreamSources.tvSources.size) {
                    _state.update {
                        it.copy(
                            playerLoading = false,
                            playerStreamFailed = true,
                            playerLiveHint = "No playable sources found for this episode."
                        )
                    }
                    return
                }
                _state.update { it.copy(sourceIndex = nextIdx, playerResolveToken = it.playerResolveToken + 1) }
                playCurrentEpisode()
            }
            null -> {
                val st = _state.value
                if (st.liveStreamPicker.isNotEmpty()) {
                    val next = (st.sourceIndex + 1) % st.liveStreamPicker.size
                    val link = st.liveStreamPicker[next]
                    clearPlayerStreamFailed()
                    playLiveEmbed(
                        st.playerTitle,
                        link.embedUrl,
                        link.label,
                        pickerIndex = next
                    )
                } else if (st.playerUrl.isNotBlank()) {
                    val bumped = StreamedRepository.bumpStreamNumber(st.playerUrl)
                    if (bumped != null) {
                        clearPlayerStreamFailed()
                        playLiveEmbed(
                            st.playerTitle,
                            bumped,
                            st.sourceLabel.substringAfter("· ").ifBlank { "Stream" }
                        )
                    }
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
        _state.update {
            it.copy(
                playerLoading = false,
                playerStreamFailed = false,
                playerLiveHint = null,
                playerLiveEventStartMs = 0L,
                playerStreamPlaying = true
            )
        }
    }

    fun onPlayerLiveWaiting() {
        if (_state.value.playerStreamPlaying) return
        val hint = if (isPreLiveEvent()) {
            formatLiveEventHint(_state.value.playerLiveEventStartMs)
        } else {
            "Waiting for stream to start... (This may take a moment)"
        }
        _state.update { it.copy(playerLoading = false, playerLiveHint = hint, playerStreamFailed = false) }
    }

    private fun formatLiveEventHint(dateMs: Long): String {
        if (dateMs <= 0L) {
            return "This stream is not available yet. Try another source or check back later."
        }
        val delta = dateMs - System.currentTimeMillis()
        if (delta > 60_000L) {
            val totalMin = (delta / 60_000L).toInt()
            val hours = totalMin / 60
            val mins = totalMin % 60
            return if (hours > 0) {
                "Game starts in ${hours}h ${mins}m — the player may stay blank until then."
            } else {
                "Game starts in ${mins}m — the player may stay blank until then."
            }
        }
        return "Waiting for the live stream to start…"
    }

    fun closePlayer() {
        viewModelScope.launch {
            persistCwJob?.cancel()
            persistContinueWatching()
        }
        _state.update {
            it.copy(
                playerVisible = false,
                playerUrl = "",
                playerLogoUrl = null,
                playerResolveToken = 0,
                playerLoading = false,
                playerStreamFailed = false,
                playbackPositionMs = 0L,
                playingType = null,
                playerLiveTv = false,
                playerHls = false,
                playerLiveHint = null,
                playerLiveEventStartMs = 0L,
                playerStreamPlaying = false
            )
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null, authLoading = true) }
            authService.signIn(email, password)
                .onSuccess {
                    pullCloudSafe()
                    finishOnboarding()
                    _state.update { it.copy(profileMessage = "Signed in — profiles loaded") }
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
                    pullCloudSafe()
                    finishOnboarding()
                }
                .onFailure { e ->
                    _state.update { it.copy(authLoading = false, profileError = e.message ?: "Sign up failed") }
                }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null) }
            authService.sendPasswordReset(email)
                .onSuccess {
                    _state.update { it.copy(profileMessage = "Password reset link sent to $email. Please check your spam folder.") }
                }
                .onFailure { e ->
                    _state.update { it.copy(profileError = e.message ?: "Failed to send reset email") }
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
                    _state.update { it.copy(profileError = e.message ?: "Failed to sign in as guest", authLoading = false) }
                }
        }
    }

    fun clearProfileMessage() {
        _state.update { it.copy(profileMessage = null) }
    }

    fun updateProfile(id: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null) }
            runCatching { profileStore.updateProfile(id, name) }
                .onSuccess {
                    val p = profileStore.snapshot().first
                    _state.update { it.copy(profiles = p, profileMessage = "Profile saved") }
                    syncToCloud()
                }
                .onFailure { _state.update { it.copy(profileError = "Failed to update profile") } }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            if (id == "default") {
                _state.update { it.copy(profileError = "Cannot delete Main profile") }
                return@launch
            }
            if (id == activeProfileId()) {
                switchProfile("default")
            }
            profileStore.removeProfile(id)
            val p = profileStore.snapshot().first
            _state.update { it.copy(profiles = p, profileMessage = "Profile deleted") }
            syncToCloud()
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null, authLoading = true) }
            authService.signInWithGoogle(idToken)
                .onSuccess {
                    pullCloudSafe()
                    finishOnboarding()
                }
                .onFailure { e ->
                    _state.update { it.copy(authLoading = false, profileError = e.message ?: "Google Sign-In failed") }
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
        viewModelScope.launch {
            sessionStore.clearSession()
            authService.signOut()
            _state.update { it.copy(
                isLoggedIn = false,
                sessionReady = true,
                showAuthGate = true, 
                showProfilePicker = false,
                profileMessage = null,
                profiles = emptyList(),
                activeProfileId = "default"
            ) }
        }
    }

    fun syncToCloud() {
        viewModelScope.launch {
            syncIfLoggedIn()
            _state.update { it.copy(profileMessage = "Library synced") }
        }
    }

    private suspend fun syncIfLoggedIn(): Boolean {
        if (cloudSyncBlocked()) return false
        val s = _state.value
        val (profiles, activeId) = profileStore.snapshot()
        var ok = syncService.pushAccountMeta(activeId, profiles).isSuccess
        for (profile in profiles) {
            syncService.pushProfileData(
                profileId = profile.id,
                displayName = profile.name,
                email = s.userEmail,
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
        if (cloudSyncBlocked()) return
        val account = syncService.pullAccount() ?: return

        profileStore.importFromCloud(account.profiles, account.activeProfileId)
        restoreProfileAvatarsFromCloud(account.profiles)

        val profileIds = (account.profileData.keys + profileStore.snapshot().first.map { it.id }).toSet()
        for (profileId in profileIds) {
            val cloud = account.profileData[profileId]
            val localCw = cwStore.readOnce(profileId)
            val localLists = playlistStore.readOnce(profileId)
            cwStore.replaceAll(profileId, mergeContinueWatching(localCw, cloud?.continueWatching.orEmpty()))
            playlistStore.replaceAll(profileId, mergePlaylists(localLists, cloud?.playlists.orEmpty()))
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

    private fun mergeContinueWatching(
        local: List<ContinueWatchingEntry>,
        remote: List<ContinueWatchingEntry>
    ): List<ContinueWatchingEntry> {
        if (remote.isEmpty()) return local
        if (local.isEmpty()) return remote.take(12)
        return (local + remote)
            .groupBy { "${it.type}_${it.id}" }
            .map { (_, entries) -> entries.maxBy { it.updatedAt } }
            .sortedByDescending { it.updatedAt }
            .take(12)
    }

    private fun mergePlaylists(local: List<Playlist>, remote: List<Playlist>): List<Playlist> {
        if (remote.isEmpty()) return local
        if (local.isEmpty()) return remote
        return (local + remote).distinctBy { it.id }
    }

    fun startUpdate() {
        val update = _state.value.updateInfo ?: return
        com.enigma.tv.update.InAppUpdater.downloadAndInstallUpdate(
            getApplication(),
            update.downloadUrl,
            update.latestVersion
        )
        _state.update { it.copy(updateInfo = null) }
    }

    fun dismissUpdate() {
        _state.update { it.copy(updateInfo = null) }
    }
}
