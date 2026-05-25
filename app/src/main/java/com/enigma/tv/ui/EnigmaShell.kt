package com.enigma.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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

    val layout = rememberScreenLayout()

    if (state.showAuthGate) {
        AuthGateScreen(
            layout = layout,
            loading = state.authLoading,
            error = state.profileError,
            onSignIn = viewModel::signIn,
            onSignUp = viewModel::signUp,
            onGuest = viewModel::signInGuest
        )
        return
    }

    if (state.showProfilePicker) {
        ProfilePickerGate(
            profiles = state.profiles,
            layout = layout,
            onSelectProfile = viewModel::selectProfileAndContinue,
            onAddProfile = viewModel::addProfile,
            onRenameProfile = viewModel::renameProfile,
            onRemoveProfile = viewModel::removeProfile,
            onSetAvatarIndex = viewModel::setProfileAvatarIndex,
            onSetAvatarUri = viewModel::setProfileAvatarUri
        )
        return
    }

    val bootstrapLoading = state.contentLoading && state.homeRows.isEmpty()
    val activeProfile = state.profiles.find { it.id == state.activeProfileId }
    val useBottomNav = !layout.usePermanentDrawer()

    val drawerContent: @Composable () -> Unit = {
        EnigmaDrawerContent(
            current = state.section,
            layout = layout,
            onSelect = { section ->
                viewModel.setSection(section)
                if (!layout.usePermanentDrawer()) scope.launch { drawerState.close() }
            },
            onSwitchProfile = { viewModel.showProfilePickerScreen() }
        )
    }

    val bodyContent: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                EnigmaHeader(
                    sectionLabel = if (state.section == NavSection.HOME) null else state.section.title,
                    placeholder = if (state.section == NavSection.LIVE) "Search games or channels…" else "Search movies & TV…",
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {
                        if (state.section == NavSection.LIVE) viewModel.searchLiveTv(query)
                        else viewModel.search(query)
                    },
                    onMenuClick = if (!useBottomNav) null else null,
                    activeProfile = activeProfile,
                    onProfileClick = { viewModel.showProfilePickerScreen() },
                    showSearch = state.section == NavSection.HOME || state.section == NavSection.LIVE
                )

                when {
                    state.contentLoading && !bootstrapLoading -> EnigmaLoadingRing(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        message = "LOADING"
                    )
                    state.error != null -> ErrorPanel(
                        message = state.error!!,
                        onDismiss = viewModel::clearError
                    )
                    else -> AnimatedContent(
                        targetState = state.section,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "main_section"
                    ) { section ->
                        when (section) {
                            NavSection.HOME -> UnifiedHomeContent(state, viewModel, layout)
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
                                onOpenProfilePicker = viewModel::showProfilePickerScreen
                            )
                        }
                    }
                }
            }

            val accent = if (state.playerAccentMovie) MovieAccent else TvAccent
            val tvControls = if (
                state.playerVisible &&
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

            when {
                state.playerVisible && state.playerHls -> {
                    ExoLivePlayer(
                        visible = true,
                        title = state.playerTitle,
                        streamUrl = state.playerUrl,
                        sourceLabel = state.sourceLabel,
                        logoUrl = state.playerLogoUrl,
                        streamLoading = state.playerLoading,
                        onClose = { viewModel.closePlayer() },
                        onLoadingChange = { viewModel.onPlayerPageLoading(it) }
                    )
                }
                state.playerVisible &&
                    (state.playingType == ContentType.MOVIE || state.playingType == ContentType.TV) -> {
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
                        tvControls = tvControls,
                        resolveToken = state.playerResolveToken,
                        tmdbId = state.currentMovieId ?: state.currentShowId,
                        playingType = state.playingType,
                        season = state.selectedSeason,
                        episode = state.selectedEpisode
                    )
                }
                state.playerVisible && state.playerLiveTv -> {
                    EnigmaLivePlayer(
                        visible = true,
                        title = state.playerTitle,
                        embedUrl = state.playerUrl,
                        posterUrl = state.playerLogoUrl,
                        sourceLabel = state.sourceLabel,
                        streamLoading = state.playerLoading,
                        onClose = { viewModel.closePlayer() },
                        onNextSource = { viewModel.nextSource() },
                        onLoadingChange = { viewModel.onPlayerPageLoading(it) },
                        resolveToken = state.playerResolveToken
                    )
                }
            }

            if (state.showLiveStreamPicker) {
                LiveStreamPickerDialog(
                    title = state.playerTitle,
                    streams = state.liveStreamPicker,
                    onPick = viewModel::pickLiveStream,
                    onDismiss = viewModel::dismissLiveStreamPicker
                )
            }

            if (state.showDetail) {
                MediaDetailOverlay(
                    loading = state.detailLoading,
                    detail = state.detail,
                    onClose = { viewModel.closeDetail() },
                    onPlay = { viewModel.playFromDetail() },
                    onToggleFavorite = { viewModel.toggleDetailFavorite() },
                    onSeasonChange = { viewModel.detailSeasonChange(it) },
                    onEpisodeSelect = { viewModel.detailEpisodeSelect(it) }
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (layout.usePermanentDrawer()) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = BgSidebar,
                        modifier = Modifier.width(layout.drawerWidthDp().dp)
                    ) { drawerContent() }
                },
                content = { bodyContent() }
            )
        } else if (useBottomNav) {
            Scaffold(
                containerColor = BgDark,
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
        if (bootstrapLoading) {
            EnigmaLoadingRing(
                modifier = Modifier.fillMaxSize(),
                message = "LOADING",
                fullscreen = true
            )
        }
    }
}

@Composable
private fun EnigmaDrawerContent(
    current: NavSection,
    layout: ScreenLayout,
    onSelect: (NavSection) -> Unit,
    onSwitchProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(vertical = 24.dp)
    ) {
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
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(12.dp))

        DrawerEntry(Icons.Default.Home, NavSection.HOME, current, onSelect)
        DrawerEntry(Icons.Default.LiveTv, NavSection.LIVE, current, onSelect)
        DrawerEntry(Icons.Default.Favorite, NavSection.FAVORITES, current, onSelect)
        DrawerEntry(Icons.Default.PlayCircle, NavSection.CONTINUE, current, onSelect)
        DrawerEntry(Icons.Default.PlaylistPlay, NavSection.LISTS, current, onSelect)
        DrawerEntry(Icons.Default.Person, NavSection.PROFILE, current, onSelect)
        Spacer(Modifier.weight(1f))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Switch profile") },
            label = { Text("Switch Profile") },
            selected = false,
            onClick = onSwitchProfile,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    section: NavSection,
    current: NavSection,
    onSelect: (NavSection) -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = section.title) },
        label = { Text(section.title) },
        selected = current == section,
        onClick = { onSelect(section) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = EnigmaPurple.copy(alpha = 0.25f),
            selectedIconColor = EnigmaPink,
            selectedTextColor = TextPrimary
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun UnifiedHomeContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    val pad = layout.contentPaddingDp().dp
    val cardW = layout.posterWidthDp()
    ScrollableContent(padding = androidx.compose.foundation.layout.PaddingValues(pad)) {
        val search = state.searchResults
        if (search != null) {
            if (search.movies.isNotEmpty()) {
                ContentSection("🔍 Movies") {
                    PosterRow {
                        search.movies.take(20).forEach { MediaMovieCard(it, vm, cardW) }
                    }
                }
            }
            if (search.tv.isNotEmpty()) {
                ContentSection("🔍 TV Shows") {
                    PosterRow {
                        search.tv.take(20).forEach { MediaTvCard(it, vm, cardW) }
                    }
                }
            }
            if (search.movies.isEmpty() && search.tv.isEmpty()) {
                Text("No results found.", color = TextSecondary, modifier = Modifier.padding(24.dp))
            }
        } else {
            ContinueWatchingSection(state.continueWatching, vm, cardW)
            state.homeRows.forEach { row ->
                when (row) {
                    is HomeRow.Movies -> HomeMoviesSection(row.title, row.items, vm, layout, cardW)
                    is HomeRow.TvShows -> HomeTvSection(row.title, row.items, vm, layout, cardW)
                }
            }
        }
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
private fun LiveStreamPickerDialog(
    title: String,
    streams: List<LiveStreamLink>,
    onPick: (LiveStreamLink) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick stream · $title") },
        text = {
            Column {
                streams.forEach { link ->
                    TextButton(
                        onClick = { onPick(link) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${link.label} (${link.source})", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FavoritesContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    val cardW = layout.posterWidthDp()
    ScrollableContent(padding = androidx.compose.foundation.layout.PaddingValues(layout.contentPaddingDp().dp)) {
        if (state.favorites.isEmpty()) {
            Text("No favorites yet. Tap the heart on any title.", color = TextSecondary, modifier = Modifier.padding(24.dp))
        } else {
            ContentSection("❤️ Your Favorites") {
                PosterRow { state.favorites.forEach { FavoritePosterCard(it, vm, cardW) } }
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(entries: List<ContinueWatchingEntry>, vm: EnigmaViewModel, cardW: Int) {
    ContentSection("▶ Continue Watching") {
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
    val subtitle = if (entry.type == ContentType.TV) "S${entry.season}E${entry.episode}" else "Resume"
    PosterCard(
        title = entry.name,
        posterUrl = entry.poster.ifBlank { null },
        accent = accent,
        badge = badge,
        subtitle = subtitle,
        cardWidthDp = cardW,
        onClick = { vm.resumeContinue(entry) }
    )
}

@Composable
private fun ContinueContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    ScrollableContent(padding = androidx.compose.foundation.layout.PaddingValues(layout.contentPaddingDp().dp)) {
        ContinueWatchingSection(state.continueWatching, vm, layout.posterWidthDp())
    }
}

@Composable
private fun ListsContent(state: EnigmaUiState, vm: EnigmaViewModel, layout: ScreenLayout) {
    var showCreate by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    val selected = state.playlists.find { it.id == state.selectedPlaylistId }
    val cardW = layout.posterWidthDp()

    ScrollableContent(padding = androidx.compose.foundation.layout.PaddingValues(layout.contentPaddingDp().dp)) {
        if (selected == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Lists", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New list", tint = EnigmaPink)
                }
            }
            if (state.playlists.isEmpty()) {
                Text("Create a playlist to organize movies and shows.", color = TextSecondary, modifier = Modifier.padding(8.dp))
            } else {
                state.playlists.forEach { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { vm.selectPlaylist(pl.id) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(pl.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${pl.items.size} titles", color = TextSecondary, fontSize = 12.sp)
                        }
                        IconButton(onClick = { vm.deletePlaylist(pl.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.selectPlaylist(null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(selected.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            if (selected.items.isEmpty()) {
                Text("This list is empty.", color = TextSecondary, modifier = Modifier.padding(8.dp))
            } else {
                PosterRow { selected.items.forEach { FavoritePosterCard(it, vm, cardW) } }
            }
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
        onClick = { vm.openMovieDetail(movie) }
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
        onClick = { vm.openTvDetail(show) }
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
private fun ErrorPanel(message: String, onDismiss: () -> Unit) {
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
        Button(
            onClick = onDismiss,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Dismiss")
        }
    }
}
