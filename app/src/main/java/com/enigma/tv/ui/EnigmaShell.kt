package com.enigma.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.HomeRow
import com.enigma.tv.data.LiveStreamLink
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.TvItem
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.data.canStream
import com.enigma.tv.data.comingSoonLabel
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.BgSidebar
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.MovieAccent
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary
import com.enigma.tv.ui.theme.TvAccent
import kotlinx.coroutines.launch

@Composable
fun EnigmaShell(viewModel: EnigmaViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    // Exit confirmation dialog
    var showExitDialog by remember { mutableStateOf(false) }

    val layout = rememberScreenLayout()

    if (!state.sessionReady) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BgDark)
        ) {
            EnigmaLoadingRing(
                modifier = Modifier.fillMaxSize(),
                message = "STARTING UP",
                fullscreen = true
            )
        }
        return
    }

    if (state.showAuthGate) {
        AuthGateScreen(
            layout = layout,
            loading = state.authLoading,
            error = state.profileError,
            onSignIn = viewModel::signIn,
            onSignUp = viewModel::signUp,
            onGuest = viewModel::signInGuest,
            onClearError = { viewModel.clearAuthError() }
        )
        return
    }

    // Cloud-refresh effect: fires when picker is shown; internal guard makes it safe outside picker too.
    LaunchedEffect(state.isLoggedIn, state.showProfilePicker) {
        if (!state.isLoggedIn || !state.showProfilePicker) return@LaunchedEffect
        kotlinx.coroutines.delay(8000)
        if (state.openingProfileId != null) return@LaunchedEffect
        viewModel.refreshProfilesFromCloud()
    }

    // Only show bootstrap loading when the picker is NOT on screen to avoid double overlays.
    val bootstrapLoading = state.contentLoading && state.homeRows.isEmpty() && !state.showProfilePicker
    val activeProfile = state.profiles.find { it.id == state.activeProfileId }
    val useBottomNav = !layout.usePermanentDrawer()

    LaunchedEffect(state.section, state.homeRows.isEmpty(), state.contentLoading, state.showProfilePicker, state.showAuthGate, state.error) {
        if (state.section == NavSection.HOME && state.homeRows.isEmpty() && !state.contentLoading && !state.showProfilePicker && !state.showAuthGate && state.error == null) {
            viewModel.loadHome()
        }
    }

    // Back handler: navigate up through sections, then show exit prompt
    androidx.activity.compose.BackHandler(
        enabled = !state.showDetail && !state.playerVisible && !state.showProfilePicker
    ) {
        when {
            state.section != NavSection.HOME -> viewModel.setSection(NavSection.HOME)
            else -> showExitDialog = true
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit EnigmaTV?", color = TextPrimary) },
            text = { Text("Are you sure you want to exit?", color = TextSecondary) },
            confirmButton = {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                TextButton(onClick = {
                    (ctx as? android.app.Activity)?.finish()
                }) { Text("Exit", color = EnigmaPink) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A2E)
        )
    }

    // Update dialog
    if (state.updateInfo != null && !state.showProfilePicker && state.openingProfileId == null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update Available", color = TextPrimary) },
            text = { Text("Version ${state.updateInfo!!.latestVersion} is available to download.\n\n${state.updateInfo!!.releaseNotes}", color = TextSecondary) },
            confirmButton = {
                var dlFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { viewModel.startUpdate() },
                    modifier = Modifier
                        .onFocusChanged { dlFocused = it.isFocused }
                        .then(if (dlFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier),
                    colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple)
                ) {
                    Text("Download & Install", color = Color.White)
                }
            },
            dismissButton = {
                var laterFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { viewModel.dismissUpdate() },
                    modifier = Modifier
                        .onFocusChanged { laterFocused = it.isFocused }
                        .then(if (laterFocused) Modifier.border(2.dp, TextSecondary, RoundedCornerShape(8.dp)) else Modifier)
                ) {
                    Text("Later", color = if (laterFocused) TextPrimary else TextSecondary)
                }
            },
            containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A2E)
        )
    }

    // Sidebar: on TV always show an icon rail (72dp); expands to full width when any item has focus
    val TV_RAIL_WIDTH = 72
    var isTvDrawerFocused by remember { mutableStateOf(false) }
    val tvDrawerWidth by animateDpAsState(
        if (isTvDrawerFocused) layout.drawerWidthDp().dp else TV_RAIL_WIDTH.dp,
        label = "tvDrawerWidth"
    )

    val drawerContent: @Composable () -> Unit = {
        EnigmaDrawerContent(
            current = state.section,
            layout = layout,
            isExpanded = if (layout == ScreenLayout.TV) isTvDrawerFocused else true,
            profiles = state.profiles,
            activeProfileId = state.activeProfileId,
            onSwitchProfileQuick = { profileId ->
                viewModel.switchProfile(profileId)
                if (!layout.usePermanentDrawer()) scope.launch { drawerState.close() }
            },
            onSelect = { section ->
                viewModel.setSection(section)
                if (!layout.usePermanentDrawer()) scope.launch { drawerState.close() }
            },
            onSwitchProfile = { viewModel.showProfilePickerScreen() },
            onAnyItemFocused = { hasFocus -> if (layout == ScreenLayout.TV) isTvDrawerFocused = hasFocus }
        )
    }

    val bodyContent: @Composable () -> Unit = {
        AppAmbientBackground {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // On TV the permanent rail handles navigation — no extra menu button needed

                if (!layout.usePermanentDrawer()) {
                    EnigmaHeader(
                        sectionLabel = if (state.section == NavSection.HOME) null else state.section.title,
                        placeholder = if (state.section == NavSection.LIVE) "Search games or channels…" else "Search movies & TV…",
                        query = query,
                        onQueryChange = {
                            query = it
                            if (state.section == NavSection.HOME) viewModel.onSearchQueryChanged(it)
                        },
                        onSearch = {
                            if (state.section == NavSection.LIVE) viewModel.searchLiveTv(query)
                            else viewModel.search(query)
                        },
                        searchSuggestions = if (state.section == NavSection.HOME) state.searchSuggestions else emptyList(),
                        onSuggestionClick = { suggestion ->
                            query = suggestion.title
                            viewModel.pickSearchSuggestion(suggestion)
                        },
                        onDismissSuggestions = viewModel::clearSearchSuggestions,
                        onMenuClick = null,
                        activeProfile = activeProfile,
                        onProfileClick = { viewModel.showProfilePickerScreen() },
                        showSearch = (state.section == NavSection.HOME || state.section == NavSection.SEARCH)
                    )
                }

                when {
                    // Don't replace SEARCH screen with a loading spinner — that unmounts the text field
                    state.contentLoading && !bootstrapLoading && state.section != NavSection.SEARCH -> EnigmaLoadingRing(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        message = "LOADING"
                    )
                    state.error != null -> ErrorPanel(
                        message = state.error!!,
                        onDismiss = viewModel::clearError,
                        onRetry = if (state.section == NavSection.HOME) {
                            { viewModel.loadHome() }
                        } else null
                    )
                    else -> AnimatedContent(
                        targetState = state.section,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 8 })
                        },
                        label = "main_section"
                    ) { section ->
                        when (section) {
                            NavSection.HOME -> UnifiedHomeContent(state, viewModel, layout)
                            NavSection.SEARCH -> if (layout.usePermanentDrawer()) {
                                TvSearchContent(state, viewModel, layout)
                            } else {
                                UnifiedHomeContent(state, viewModel, layout)
                            }
                            NavSection.LIVE -> LiveTvScreen(
                                live = state.liveTv,
                                layout = layout,
                                onTab = viewModel::setLiveTvTab,
                                onSearch = viewModel::searchLiveTv,
                                onReload = viewModel::loadLiveTv,
                                onPlayChannel = viewModel::playIptvChannel,
                                onPlayMatch = viewModel::playLiveMatch,
                                onToggleFavorite = viewModel::toggleLiveChannelFavorite,
                                onGroupFilter = viewModel::setLiveChannelGroupFilter,
                                onFavoritesOnly = viewModel::toggleLiveFavoritesOnly,
                                onQuickPick = viewModel::liveQuickPick
                            )
                            NavSection.FAVORITES -> FavoritesContent(state, viewModel, layout)
                            NavSection.CONTINUE -> ContinueContent(state, viewModel, layout)
                            NavSection.LISTS -> ListsContent(state, viewModel, layout)
                            NavSection.PROFILE -> ProfileScreen(
                                isLoggedIn = state.isLoggedIn,
                                email = state.userEmail,
                                displayName = state.userDisplayName,
                                profiles = state.profiles,
                                activeProfileId = state.activeProfileId,
                                statusMessage = state.profileMessage,
                                error = state.profileError,
                                onSignIn = viewModel::signIn,
                                onSignUp = viewModel::signUp,
                                onGuest = viewModel::signInGuest,
                                onSignOut = viewModel::signOut,
                                onSync = {},
                                onSwitchProfile = viewModel::switchProfile,
                                onAddProfile = viewModel::addProfile,
                                onRemoveProfile = viewModel::removeProfile,
                                onOpenProfilePicker = viewModel::showProfilePickerScreen,
                                layout = layout
                            )
                        }
                    }
                }
            }
        }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ── LAYER 1: Main app content – ALWAYS composed, even while picker is visible ──
        if (layout.usePermanentDrawer()) {
            PermanentNavigationDrawer(
                drawerContent = {
                    // Always render the sheet — starts at rail width (72dp), expands on focus.
                    // Conditional mounting caused the flicker: items would mount, immediately
                    // steal focus, then the Box lost hasFocus and collapsed again.
                    PermanentDrawerSheet(
                        drawerContainerColor = BgSidebar,
                        modifier = Modifier
                            .width(tvDrawerWidth)
                            .fillMaxHeight()
                    ) { drawerContent() }
                },
                content = { bodyContent() }
            )
        } else if (useBottomNav) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NetflixBottomBar(
                        current = state.section,
                        onSelect = { viewModel.setSection(it) }
                    )
                },
                content = { padding ->
                    Box(Modifier.padding(padding)) { bodyContent() }
                }
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = BgSidebar,
                        modifier = Modifier.width(layout.drawerWidthDp().dp)
                    ) { drawerContent() }
                },
                content = { bodyContent() }
            )
        }

        // ── LAYER 2: Bootstrap loading (only when genuinely no content and picker not covering) ──
        if (bootstrapLoading) {
            EnigmaLoadingRing(
                modifier = Modifier.fillMaxSize().focusable(),
                message = "LOADING",
                fullscreen = true
            )
        }

        // ── LAYER 3: Profile picker overlay ──
        // The picker sits on top of the main content. Because the main content (Layer 1)
        // stays composed underneath, the TV focus tree is always valid. When this block
        // is removed from composition the remote focus falls to the drawer items below.
        if (state.showProfilePicker) {
            Box(Modifier.fillMaxSize()) {
                ProfilePickerGate(
                    profiles = state.profiles,
                    activeProfileId = state.activeProfileId,
                    openingProfileId = state.openingProfileId,
                    layout = layout,
                    isLoggedIn = state.isLoggedIn,
                    userEmail = state.userEmail,
                    onSelectProfile = viewModel::selectProfileAndContinue,
                    onAddProfile = viewModel::addProfile,
                    onRenameProfile = viewModel::renameProfile,
                    onRemoveProfile = viewModel::removeProfile,
                    onSetAvatarIndex = viewModel::setProfileAvatarIndex,
                    onSetAvatarUri = viewModel::setProfileAvatarUri,
                    onSignIn = { viewModel.showAuthGateFromProfile() }
                )
                // ALL loading overlays are focusable — TV remote always has a valid target
                when {
                    state.openingProfileId != null -> {
                        val name = state.profiles.find { it.id == state.openingProfileId }?.name ?: "profile"
                        EnigmaLoadingRing(
                            modifier = Modifier.fillMaxSize().focusable(),
                            message = "OPENING $name",
                            fullscreen = true
                        )
                    }
                    state.profiles.isEmpty() -> {
                        EnigmaLoadingRing(
                            modifier = Modifier.fillMaxSize().focusable(),
                            message = "LOADING PROFILES",
                            fullscreen = true
                        )
                    }
                }
            }
        }

        // ── LAYER 4: Detail overlay – sits above drawer + body, owns all focus ──
        if (state.showDetail) {
            MediaDetailOverlay(
                loading = state.detailLoading,
                detail = state.detail,
                isTv = layout.usePermanentDrawer(),
                onClose = { viewModel.closeDetail() },
                onPlay = { viewModel.playFromDetail() },
                onRestart = { viewModel.playFromDetail(restart = true) },
                onRemoveFromHistory = {
                    state.detail?.let { viewModel.removeFromContinue(it.id, it.type) }
                    viewModel.closeDetail()
                },
                onToggleFavorite = { viewModel.toggleDetailFavorite() },
                onSeasonChange = { viewModel.detailSeasonChange(it) },
                onEpisodeSelect = { viewModel.detailEpisodeSelect(it) }
            )
        }

        EnigmaPlayerOverlay(state = state, viewModel = viewModel, layout = layout)
    }
}

@Composable
private fun EnigmaPlayerOverlay(
    state: EnigmaUiState,
    viewModel: EnigmaViewModel,
    layout: ScreenLayout
) {
    if (!state.playerVisible) return

    androidx.activity.compose.BackHandler { viewModel.closePlayer() }

    val accent = if (state.playerAccentMovie) MovieAccent else TvAccent
    val tvControls = if (
        state.playingType == ContentType.TV &&
        state.seasons.isNotEmpty()
    ) {
        TvPlayerControls(
            seasons = state.seasons,
            episodes = state.episodes,
            selectedSeason = state.selectedSeason,
            selectedEpisode = state.selectedEpisode,
            onSeasonChange = { viewModel.onSeasonChange(it) },
            onEpisodeChange = { viewModel.onEpisodeChange(it) }
        )
    } else null

    val showNext = state.playingType != null || state.playerLiveTv || state.playerHls
    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        var isNativePlayerActive by remember { mutableStateOf(false) }

        PlayerFullscreenHost(
            title = state.playerTitle,
            subtitle = state.sourceLabel,
            posterUrl = state.playerLogoUrl,
            accent = accent,
            layout = layout,
            onClose = { viewModel.closePlayer() },
            onNextSource = { viewModel.nextSource() },
            showNextSource = showNext,
            streamFailed = state.playerStreamFailed,
            streamLoading = state.playerLoading && !state.playerStreamFailed,
            liveWaitingMessage = state.playerLiveHint,
            streamPlaying = state.playerStreamPlaying,
            tvControls = tvControls,
            onPrevEpisode = { viewModel.playAdjacentEpisode(forward = false) },
            onNextEpisode = { viewModel.playAdjacentEpisode(forward = true) },
            hasPrevEpisode = viewModel.hasAdjacentEpisode(forward = false),
            hasNextEpisode = viewModel.hasAdjacentEpisode(forward = true),
            isNativePlayerActive = isNativePlayerActive || state.playerHls
        ) { dispatcher ->
            when {
                state.playerHls -> ExoLivePlayer(
                    visible = true,
                    title = state.playerTitle,
                    streamUrl = state.playerUrl,
                    sourceLabel = state.sourceLabel,
                    logoUrl = state.playerLogoUrl,
                    streamLoading = state.playerLoading,
                    isLiveBroadcast = true,
                    showNextSource = showNext,
                    onNextSource = { viewModel.nextSource() },
                    onClose = { viewModel.closePlayer() },
                    onLoadingChange = { viewModel.onPlayerPageLoading(it) },
                    useExternalChrome = true,
                    actionDispatcher = dispatcher,
                    modifier = Modifier.fillMaxSize()
                )
                state.playingType == ContentType.MOVIE || state.playingType == ContentType.TV -> {
                    EnigmaMediaPlayer(
                        visible = true,
                        title = state.playerTitle,
                        embedUrl = state.playerUrl,
                        posterUrl = state.playerLogoUrl,
                        accent = accent,
                        sourceLabel = state.sourceLabel,
                        streamLoading = state.playerLoading,
                        onClose = { viewModel.closePlayer() },
                        onNextSource = { viewModel.nextSource() },
                        onLoadingChange = { viewModel.onPlayerPageLoading(it) },
                        onPlaybackEnded = viewModel::onEpisodeFinished,
                        onPlaybackPositionMs = viewModel::onPlaybackPositionMs,
                        onPlaybackDurationMs = viewModel::onPlaybackDurationMs,
                        onNativePlayerActive = { isNativePlayerActive = it },
                        startPositionMs = state.playbackPositionMs,
                        tvControls = tvControls,
                        resolveToken = state.playerResolveToken,
                        tmdbId = state.currentMovieId ?: state.currentShowId,
                        playingType = state.playingType,
                        season = state.selectedSeason,
                        episode = state.selectedEpisode,
                        useExternalChrome = true,
                        actionDispatcher = dispatcher,
                        contentModifier = Modifier.fillMaxSize()
                    )
                }
                state.playerLiveTv -> {
                    EnigmaLivePlayer(
                        visible = true,
                        title = state.playerTitle,
                        embedUrl = state.playerUrl,
                        posterUrl = state.playerLogoUrl,
                        sourceLabel = state.sourceLabel,
                        streamLoading = state.playerLoading,
                        streamFailed = state.playerStreamFailed,
                        onClose = { viewModel.closePlayer() },
                        onNextSource = { viewModel.nextSource() },
                        onLoadingChange = { viewModel.onPlayerPageLoading(it) },
                        onStreamFailed = viewModel::onPlayerStreamFailed,
                        onPlaybackReady = viewModel::onPlayerPlaybackReady,
                        onLiveWaiting = viewModel::onPlayerLiveWaiting,
                        onNativePlayerActive = { isNativePlayerActive = it },
                        resolveToken = state.playerResolveToken,
                        useExternalChrome = true,
                        actionDispatcher = dispatcher,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun EnigmaDrawerContent(
    current: NavSection,
    layout: ScreenLayout,
    isExpanded: Boolean,
    profiles: List<ViewerProfile> = emptyList(),
    activeProfileId: String? = null,
    onSwitchProfileQuick: (String) -> Unit = {},
    onSelect: (NavSection) -> Unit,
    onSwitchProfile: () -> Unit,
    onAnyItemFocused: ((Boolean) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(vertical = 24.dp)
    ) {
        if (isExpanded) {
            Text(
                text = ENIGMA_TV_BRAND,
                color = EnigmaPurple,
                fontSize = if (layout == ScreenLayout.TV) 26.sp else 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
            )
            Text(
                text = "Stream movies & TV",
                color = EnigmaPink.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            if (profiles.size > 1) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "SWITCH PROFILE",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profiles.take(5).forEach { profile ->
                        val isActive = profile.id == activeProfileId
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable(enabled = !isActive)
                                .clickable(enabled = !isActive) { onSwitchProfileQuick(profile.id) }
                                .border(
                                    width = if (isFocused || isActive) 2.dp else 0.dp,
                                    color = if (isFocused) Color.White else if (isActive) EnigmaPink else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileAvatarCircle(
                                profile = profile,
                                selected = false,
                                sizeDp = 42,
                                showEditBadge = false,
                                showName = false,
                                onClick = null
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }

        DrawerEntry(Icons.Default.Home, NavSection.HOME, current, isExpanded, onSelect, onAnyItemFocused)
        DrawerEntry(Icons.Default.Search, NavSection.SEARCH, current, isExpanded, onSelect, onAnyItemFocused)
        DrawerEntry(Icons.Default.LiveTv, NavSection.LIVE, current, isExpanded, onSelect, onAnyItemFocused)
        DrawerEntry(Icons.Default.Favorite, NavSection.FAVORITES, current, isExpanded, onSelect, onAnyItemFocused)
        DrawerEntry(Icons.Default.PlayCircle, NavSection.CONTINUE, current, isExpanded, onSelect, onAnyItemFocused)
        DrawerEntry(Icons.Default.PlaylistPlay, NavSection.LISTS, current, isExpanded, onSelect, onAnyItemFocused)
        DrawerEntry(Icons.Default.Person, NavSection.PROFILE, current, isExpanded, onSelect, onAnyItemFocused)
        Spacer(Modifier.weight(1f))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Switch profile") },
            label = { if (isExpanded) Text("Switch Profile") },
            selected = false,
            onClick = onSwitchProfile,
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .onFocusChanged { onAnyItemFocused?.invoke(it.hasFocus) }
        )
    }
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    section: NavSection,
    current: NavSection,
    isExpanded: Boolean,
    onSelect: (NavSection) -> Unit,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = section.title) },
        label = { if (isExpanded) Text(section.title, fontWeight = if (current == section) FontWeight.Bold else FontWeight.Normal) },
        selected = current == section,
        onClick = { onSelect(section) },
        modifier = Modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .onFocusChanged {
                isFocused = it.isFocused
                // hasFocus is true even when a child (e.g. ripple) has focus
                onFocusChange?.invoke(it.hasFocus)
            },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = if (isFocused) EnigmaPurple.copy(alpha = 0.55f) else EnigmaPurple.copy(alpha = 0.3f),
            selectedIconColor = if (isFocused) Color.White else EnigmaPink,
            selectedTextColor = if (isFocused) Color.White else TextPrimary,
            unselectedContainerColor = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            unselectedIconColor = if (isFocused) Color.White else TextSecondary,
            unselectedTextColor = if (isFocused) Color.White else TextSecondary
        )
    )
}

@Composable
private fun UnifiedHomeContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    val pad = layout.contentPaddingDp().dp
    val isTv = layout.usePermanentDrawer()

    if (isTv) {
        // TV: LazyColumn gives D-Pad vertical scroll for free; each row is a
        // horizontally-scrollable TvPosterRow so Left/Right moves between cards.
        LazyColumn(
            contentPadding = PaddingValues(horizontal = pad, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
        ) {
            val search = state.searchResults
            if (search != null) {
                if (search.movies.isNotEmpty()) item {
                    TvContentSection("Movies") {
                        TvPosterRow {
                            search.movies.take(20).forEach { MediaMovieCard(it, vm, layout.posterWidthDp()) }
                        }
                    }
                }
                if (search.tv.isNotEmpty()) item {
                    TvContentSection("TV Shows") {
                        TvPosterRow {
                            search.tv.take(20).forEach { MediaTvCard(it, vm, layout.posterWidthDp()) }
                        }
                    }
                }
            } else {
                if (state.continueWatching.isNotEmpty()) item {
                    TvContentSection("Continue Watching") {
                        TvPosterRow {
                            state.continueWatching.forEach { ContinueWatchingCard(it, vm, layout.posterWidthDp()) }
                        }
                    }
                }
                state.homeRows.forEach { row ->
                    item {
                        when (row) {
                            is HomeRow.Movies -> TvContentSection(cleanRowTitle(row.title)) {
                                TvPosterRow {
                                    row.items.forEach { MediaMovieCard(it, vm, layout.posterWidthDp()) }
                                }
                            }
                            is HomeRow.TvShows -> TvContentSection(cleanRowTitle(row.title)) {
                                TvPosterRow {
                                    row.items.forEach { MediaTvCard(it, vm, layout.posterWidthDp()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        ScrollableContent(padding = PaddingValues(pad)) {
            val search = state.searchResults
            if (search != null) {
                if (search.movies.isNotEmpty()) {
                    ContentSection("🔍 Movies") {
                        PosterRow { search.movies.take(20).forEach { MediaMovieCard(it, vm, layout.posterWidthDp()) } }
                    }
                }
                if (search.tv.isNotEmpty()) {
                    ContentSection("🔍 TV Shows") {
                        PosterRow { search.tv.take(20).forEach { MediaTvCard(it, vm, layout.posterWidthDp()) } }
                    }
                }
                if (search.movies.isEmpty() && search.tv.isEmpty()) {
                    androidx.compose.material3.Text("No results found.", color = TextSecondary, modifier = androidx.compose.ui.Modifier.padding(24.dp))
                }
            } else {
                HomeQuickNav(current = state.section, onSelect = vm::setSection)
                pickFeaturedMovie(state.homeRows)?.let { featured ->
                    HomeHeroBanner(
                        movie = featured,
                        layout = layout,
                        onPlay = { vm.playMovie(featured) },
                        onDetails = { vm.openMovieDetail(featured) }
                    )
                }
                ContinueWatchingSection(state.continueWatching, vm, layout.posterWidthDp())
                state.homeRows.forEach { row ->
                    when (row) {
                        is HomeRow.Movies -> HomeMoviesSection(row.title, row.items, vm, layout, layout.posterWidthDp())
                        is HomeRow.TvShows -> HomeTvSection(row.title, row.items, vm, layout, layout.posterWidthDp())
                    }
                }
            }
        }
    }
}

@Composable
private fun TvContentSection(title: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        // Accent bar + title row — Disney+ / Apple TV+ style
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = androidx.compose.ui.Modifier.padding(bottom = 14.dp, start = 2.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(EnigmaPurple, EnigmaPink)
                        )
                    )
            )
            androidx.compose.material3.Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                modifier = androidx.compose.ui.Modifier.padding(start = 10.dp)
            )
        }
        content()
    }
}

@Composable
private fun HomeMoviesSection(
    title: String,
    movies: List<MovieItem>,
    vm: EnigmaViewModel,
    layout: ScreenLayout,
    cardW: Int
) {
    ContentSection(title) {
        PosterRow { movies.forEach { MediaMovieCard(it, vm, layout.posterWidthDp()) } }
    }
}

@Composable
private fun HomeTvSection(
    title: String,
    shows: List<TvItem>,
    vm: EnigmaViewModel,
    layout: ScreenLayout,
    cardW: Int
) {
    ContentSection(title) {
        PosterRow { shows.forEach { MediaTvCard(it, vm, layout.posterWidthDp()) } }
    }
}



@Composable
private fun TvSearchContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    val pad = layout.contentPaddingDp().dp
    val cardW = layout.posterWidthDp()
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        runCatching { searchFocusRequester.requestFocus() }
    }

    // Trigger search with debounce as user types
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            kotlinx.coroutines.delay(400)
            vm.search(query.trim())
        } else if (query.isBlank()) {
            vm.clearSearch()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = pad, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Large prominent search field
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    "Search movies & TV shows…",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 20.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = EnigmaPurple,
                    modifier = Modifier.size(28.dp)
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextPrimary,
                fontSize = 20.sp
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EnigmaPurple,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedContainerColor = Color.White.copy(alpha = 0.07f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                cursorColor = EnigmaPink
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        )

        val results = state.searchResults
        when {
            query.trim().length < 2 -> {
                // Prompt
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Type to search movies & TV shows",
                        color = TextSecondary,
                        fontSize = 18.sp
                    )
                }
            }
            state.contentLoading -> {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    EnigmaLoadingRing(message = "SEARCHING")
                }
            }
            results != null -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (results.movies.isNotEmpty()) item {
                        TvContentSection("Movies") {
                            TvPosterRow {
                                results.movies.take(20).forEach { MediaMovieCard(it, vm, cardW) }
                            }
                        }
                    }
                    if (results.tv.isNotEmpty()) item {
                        TvContentSection("TV Shows") {
                            TvPosterRow {
                                results.tv.take(20).forEach { MediaTvCard(it, vm, cardW) }
                            }
                        }
                    }
                    if (results.movies.isEmpty() && results.tv.isEmpty()) item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No results for \"$query\"", color = TextSecondary, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    val cardW = layout.posterWidthDp()
    val isTv = layout.usePermanentDrawer()
    
    if (isTv) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = layout.contentPaddingDp().dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                if (state.favorites.isEmpty()) {
                    Text("No favorites yet. Tap the heart on any title.", color = TextSecondary, modifier = Modifier.padding(24.dp))
                } else {
                    TvContentSection("Your Favorites") {
                        TvPosterRow { state.favorites.forEach { FavoritePosterCard(it, vm, cardW) } }
                    }
                }
            }
        }
    } else {
        ScrollableContent(padding = androidx.compose.foundation.layout.PaddingValues(layout.contentPaddingDp().dp)) {
            if (state.favorites.isEmpty()) {
                Text("No favorites yet. Tap the heart on any title.", color = TextSecondary, modifier = Modifier.padding(24.dp))
            } else {
                ContentSection("Your Favorites") {
                    PosterRow { state.favorites.forEach { FavoritePosterCard(it, vm, cardW) } }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(entries: List<ContinueWatchingEntry>, vm: EnigmaViewModel, cardW: Int) {
    ContentSection("Continue Watching") {
        if (entries.isEmpty()) {
            Text(
                "Titles you play will appear here — movies and TV.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        } else {
            PosterRow { entries.forEach { ContinueWatchingCard(it, vm, cardW) } }
        }
    }
}

@Composable
private fun ContinueWatchingCard(entry: ContinueWatchingEntry, vm: EnigmaViewModel, cardW: Int) {
    val accent = if (entry.type == ContentType.MOVIE) MovieAccent else TvAccent
    val badge = if (entry.type == ContentType.MOVIE) "MOVIE" else "TV"

    // Progress / time-remaining subtitle
    val progressFraction = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else if (entry.progressPercent > 0) {
        entry.progressPercent / 100f
    } else null

    val remainingMs = if (entry.durationMs > 0L) (entry.durationMs - entry.positionMs).coerceAtLeast(0L) else null

    val episodeTag = if (entry.type == ContentType.TV) "S${entry.season}E${entry.episode}" else null

    var showRemove by remember { mutableStateOf(false) }

    Box {
        PosterCard(
            title = entry.name,
            posterUrl = entry.poster.ifBlank { null },
            accent = accent,
            badge = badge,
            episodeTag = episodeTag,
            progress = progressFraction,
            remainingMs = remainingMs,
            cardWidthDp = cardW,
            onClick = { vm.openDetailFromContinue(entry) },
            onLongClickPlay = { showRemove = true }
        )
        if (showRemove) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .clickable { showRemove = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = {
                        showRemove = false
                        vm.removeFromContinue(entry)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = EnigmaPink, modifier = Modifier.size(28.dp))
                    }
                    Text("Remove", color = EnigmaPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
private fun ContinueContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    val isTv = layout.usePermanentDrawer()
    val cardW = layout.posterWidthDp()
    
    if (isTv) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = layout.contentPaddingDp().dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                TvContentSection("Continue Watching") {
                    if (state.continueWatching.isEmpty()) {
                        Text(
                            "Titles you play will appear here — movies and TV.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    } else {
                        TvPosterRow { state.continueWatching.forEach { ContinueWatchingCard(it, vm, cardW) } }
                    }
                }
            }
        }
    } else {
        ScrollableContent(padding = androidx.compose.foundation.layout.PaddingValues(layout.contentPaddingDp().dp)) {
            ContinueWatchingSection(state.continueWatching, vm, layout.posterWidthDp())
        }
    }
}

@Composable
private fun ListsContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    var showCreate by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    val selected = state.playlists.find { it.id == state.selectedPlaylistId }
    val cardW = layout.posterWidthDp()
    val isTv = layout.usePermanentDrawer()

    val content = @Composable {
        if (selected == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Lists", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                var addFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { showCreate = true },
                    modifier = Modifier
                        .onFocusChanged { addFocused = it.isFocused }
                        .then(if (addFocused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New list", tint = EnigmaPink)
                }
            }
            if (state.playlists.isEmpty()) {
                Text("Create a playlist to organize movies and shows.", color = TextSecondary, modifier = Modifier.padding(8.dp))
            } else {
                state.playlists.forEach { pl ->
                    var itemFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (itemFocused) EnigmaPurple.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f))
                            .onFocusChanged { itemFocused = it.isFocused }
                            .clickable { vm.selectPlaylist(pl.id) }
                            .then(if (itemFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(pl.name, color = if (itemFocused) Color.White else TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${pl.items.size} titles", color = if (itemFocused) Color.White.copy(alpha = 0.8f) else TextSecondary, fontSize = 12.sp)
                        }
                        var deleteFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { vm.deletePlaylist(pl.id) },
                            modifier = Modifier
                                .onFocusChanged { deleteFocused = it.isFocused }
                                .then(if (deleteFocused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (itemFocused) Color.White else TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var backFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { vm.selectPlaylist(null) },
                    modifier = Modifier
                        .onFocusChanged { backFocused = it.isFocused }
                        .then(if (backFocused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(selected.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            if (selected.items.isEmpty()) {
                Text("This list is empty.", color = TextSecondary, modifier = Modifier.padding(8.dp))
            } else {
                if (isTv) {
                    TvPosterRow { selected.items.forEach { FavoritePosterCard(it, vm, cardW) } }
                } else {
                    PosterRow { selected.items.forEach { FavoritePosterCard(it, vm, cardW) } }
                }
            }
        }
    }

    if (isTv) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = layout.contentPaddingDp().dp, vertical = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { content() }
        }
    } else {
        ScrollableContent(padding = PaddingValues(layout.contentPaddingDp().dp)) {
            content()
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(value = newListName, onValueChange = { newListName = it }, label = { Text("List name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createPlaylist(newListName)
                    newListName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MediaMovieCard(movie: MovieItem, vm: EnigmaViewModel, cardW: Int) {
    val state by vm.state.collectAsState()
    val fav = state.favorites.any { it.id == movie.id && it.type == ContentType.MOVIE }
    val subtitle = movie.comingSoonLabel() ?: if (!movie.canStream()) "Unavailable" else null
    PosterCard(
        title = "${movie.title} (${movie.year})",
        posterUrl = movie.posterUrl,
        accent = MovieAccent,
        badge = "MOVIE",
        subtitle = subtitle,
        cardWidthDp = cardW,
        isFavorite = fav,
        onFavoriteClick = { vm.toggleFavorite(movie.toFavorite()) },
        onClick = { vm.openMovieDetail(movie) },
        onLongClickPlay = if (movie.canStream()) ({ vm.playMovie(movie) }) else null
    )
}

@Composable
private fun MediaTvCard(show: TvItem, vm: EnigmaViewModel, cardW: Int) {
    val state by vm.state.collectAsState()
    val fav = state.favorites.any { it.id == show.id && it.type == ContentType.TV }
    PosterCard(
        title = "${show.displayName} (${show.year})",
        posterUrl = show.posterUrl,
        accent = TvAccent,
        badge = "TV",
        subtitle = show.comingSoonLabel() ?: if (!show.canStream()) "Unavailable" else null,
        cardWidthDp = cardW,
        isFavorite = fav,
        onFavoriteClick = { vm.toggleFavorite(show.toFavorite()) },
        onClick = { vm.openTvDetail(show) },
        onLongClickPlay = if (show.canStream()) ({
            vm.selectShow(show.id, show.displayName, 1, 1)
        }) else null
    )
}

@Composable
private fun FavoritePosterCard(item: FavoriteItem, vm: EnigmaViewModel, cardW: Int) {
    val state by vm.state.collectAsState()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val accent = if (item.type == ContentType.MOVIE) MovieAccent else TvAccent

    Box {
        PosterCard(
            title = "${item.title} (${item.year})",
            posterUrl = item.poster.ifBlank { null },
            accent = accent,
            badge = if (item.type == ContentType.MOVIE) "MOVIE" else "TV",
            cardWidthDp = cardW,
            isFavorite = true,
            onFavoriteClick = { vm.toggleFavorite(item) },
            onClick = { vm.playFavorite(item) }
        )
        if (state.playlists.isNotEmpty()) {
            IconButton(
                onClick = { showPlaylistPicker = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            ) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to list", tint = EnigmaPink)
            }
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to list") },
            text = {
                Column {
                    state.playlists.forEach { pl ->
                        TextButton(
                            onClick = {
                                vm.addToPlaylist(pl.id, item)
                                showPlaylistPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(pl.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlaylistPicker = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Back", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            message,
            color = Color(0xFFCC4444),
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = EnigmaPink),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Retry")
                }
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Dismiss")
            }
        }
    }
}
