package com.enigma.tv.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
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
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.TvItem
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

    if (state.showSplash) {
        EnigmaLoadingRing(
            fullscreen = true,
            message = "LOADING ENIGMATV"
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = BgSidebar,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 24.dp)
                ) {
                    Text(
                        text = ENIGMA_TV_BRAND,
                        color = EnigmaPurple,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "Stream movies & TV",
                        color = EnigmaPink.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 0.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))

                    DrawerEntry(Icons.Default.Home, NavSection.HOME, state.section) {
                        viewModel.setSection(NavSection.HOME)
                        scope.launch { drawerState.close() }
                    }
                    DrawerEntry(Icons.Default.Favorite, NavSection.FAVORITES, state.section) {
                        viewModel.setSection(NavSection.FAVORITES)
                        scope.launch { drawerState.close() }
                    }
                    DrawerEntry(Icons.Default.PlayCircle, NavSection.CONTINUE, state.section) {
                        viewModel.setSection(NavSection.CONTINUE)
                        scope.launch { drawerState.close() }
                    }
                    DrawerEntry(Icons.Default.PlaylistPlay, NavSection.LISTS, state.section) {
                        viewModel.setSection(NavSection.LISTS)
                        viewModel.selectPlaylist(null)
                        scope.launch { drawerState.close() }
                    }
                }
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                EnigmaHeader(
                    sectionLabel = state.section.title,
                    placeholder = "Search movies & TV on EnigmaTV…",
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { viewModel.search(query) },
                    onMenuClick = { scope.launch { drawerState.open() } }
                )

                when {
                    state.contentLoading -> EnigmaLoadingRing(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        message = "LOADING"
                    )
                    state.error != null -> Text(
                        text = state.error!!,
                        color = Color(0xFF661111),
                        modifier = Modifier.padding(40.dp)
                    )
                    else -> when (state.section) {
                        NavSection.HOME -> UnifiedHomeContent(state, viewModel)
                        NavSection.FAVORITES -> FavoritesContent(state, viewModel)
                        NavSection.CONTINUE -> ContinueContent(state, viewModel)
                        NavSection.LISTS -> ListsContent(state, viewModel)
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

            WebViewPlayer(
                visible = state.playerVisible,
                title = state.playerTitle,
                url = state.playerUrl,
                accent = accent,
                sourceLabel = state.sourceLabel,
                streamLoading = state.playerLoading,
                onClose = { viewModel.closePlayer() },
                onNextSource = { viewModel.nextSource() },
                onLoadingChange = { viewModel.onPlayerPageLoading(it) },
                tvControls = tvControls
            )
        }
    }
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    section: NavSection,
    current: NavSection,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = section.title) },
        label = { Text(section.title) },
        selected = current == section,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = EnigmaPurple.copy(alpha = 0.25f),
            selectedIconColor = EnigmaPink,
            selectedTextColor = TextPrimary
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun UnifiedHomeContent(state: EnigmaUiState, vm: EnigmaViewModel) {
    ScrollableContent {
        val search = state.searchResults
        if (search != null) {
            if (search.movies.isNotEmpty()) {
                ContentSection("🔍 Movies") {
                    PosterRow {
                        search.movies.take(10).forEach { MediaMovieCard(it, vm) }
                    }
                }
            }
            if (search.tv.isNotEmpty()) {
                ContentSection("🔍 TV Shows") {
                    PosterRow {
                        search.tv.take(10).forEach { MediaTvCard(it, vm) }
                    }
                }
            }
            if (search.movies.isEmpty() && search.tv.isEmpty()) {
                Text("No results found.", color = TextSecondary, modifier = Modifier.padding(24.dp))
            }
        } else {
            if (state.continueWatching.isNotEmpty()) {
                ContentSection("▶ Continue Watching") {
                    PosterRow {
                        state.continueWatching.take(6).forEach { entry ->
                            PosterCard(
                                title = entry.name,
                                posterUrl = entry.poster.ifBlank { null },
                                accent = TvAccent,
                                badge = "TV",
                                subtitle = "S${entry.season}E${entry.episode}",
                                onClick = {
                                    vm.selectShow(entry.id, entry.name, entry.season, entry.episode)
                                }
                            )
                        }
                    }
                }
            }
            ContentSection("🔥 Trending Movies") {
                PosterRow { state.trendingMovies.take(8).forEach { MediaMovieCard(it, vm) } }
            }
            ContentSection("📺 Trending TV") {
                PosterRow { state.trendingTv.take(8).forEach { MediaTvCard(it, vm) } }
            }
            ContentSection("⭐ Popular Movies") {
                PosterRow { state.popularMovies.take(8).forEach { MediaMovieCard(it, vm) } }
            }
            ContentSection("🌟 Popular TV") {
                PosterRow { state.popularTv.take(8).forEach { MediaTvCard(it, vm) } }
            }
            ContentSection("🆕 On The Air") {
                PosterRow { state.onTheAirTv.take(8).forEach { MediaTvCard(it, vm) } }
            }
        }
    }
}

@Composable
private fun FavoritesContent(state: EnigmaUiState, vm: EnigmaViewModel) {
    ScrollableContent {
        if (state.favorites.isEmpty()) {
            Text(
                "No favorites yet. Tap the heart on any title.",
                color = TextSecondary,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            ContentSection("❤️ Your Favorites") {
                PosterRow {
                    state.favorites.forEach { item ->
                        FavoritePosterCard(item, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueContent(state: EnigmaUiState, vm: EnigmaViewModel) {
    ScrollableContent {
        if (state.continueWatching.isEmpty()) {
            Text("Nothing in progress yet.", color = TextSecondary, modifier = Modifier.padding(24.dp))
        } else {
            ContentSection("▶ Pick up where you left off") {
                PosterRow {
                    state.continueWatching.forEach { entry ->
                        PosterCard(
                            title = entry.name,
                            posterUrl = entry.poster.ifBlank { null },
                            accent = TvAccent,
                            badge = "TV",
                            subtitle = "S${entry.season}E${entry.episode}",
                            onClick = { vm.selectShow(entry.id, entry.name, entry.season, entry.episode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListsContent(state: EnigmaUiState, vm: EnigmaViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    val selected = state.playlists.find { it.id == state.selectedPlaylistId }

    ScrollableContent {
        if (selected == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Lists", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New list", tint = EnigmaPink)
                }
            }
            if (state.playlists.isEmpty()) {
                Text(
                    "Create a playlist to organize movies and shows.",
                    color = TextSecondary,
                    modifier = Modifier.padding(8.dp)
                )
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
                            Text(
                                "${pl.items.size} titles",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
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
                PosterRow {
                    selected.items.forEach { item ->
                        FavoritePosterCard(item, vm)
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("List name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createPlaylist(newListName)
                    newListName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MediaMovieCard(movie: MovieItem, vm: EnigmaViewModel) {
    val state by vm.state.collectAsState()
    val fav = state.favorites.any { it.id == movie.id && it.type == ContentType.MOVIE }
    PosterCard(
        title = "${movie.title} (${movie.year})",
        posterUrl = movie.posterUrl,
        accent = MovieAccent,
        badge = "MOVIE",
        isFavorite = fav,
        onFavoriteClick = { vm.toggleFavorite(movie.toFavorite()) },
        onClick = { vm.playMovie(movie) }
    )
}

@Composable
private fun MediaTvCard(show: TvItem, vm: EnigmaViewModel) {
    val state by vm.state.collectAsState()
    val fav = state.favorites.any { it.id == show.id && it.type == ContentType.TV }
    PosterCard(
        title = "${show.displayName} (${show.year})",
        posterUrl = show.posterUrl,
        accent = TvAccent,
        badge = "TV",
        isFavorite = fav,
        onFavoriteClick = { vm.toggleFavorite(show.toFavorite()) },
        onClick = { vm.selectShow(show.id, show.displayName) }
    )
}

@Composable
private fun FavoritePosterCard(item: FavoriteItem, vm: EnigmaViewModel) {
    val state by vm.state.collectAsState()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val accent = if (item.type == ContentType.MOVIE) MovieAccent else TvAccent

    Box {
        PosterCard(
            title = "${item.title} (${item.year})",
            posterUrl = item.poster.ifBlank { null },
            accent = accent,
            badge = if (item.type == ContentType.MOVIE) "MOVIE" else "TV",
            isFavorite = true,
            onFavoriteClick = { vm.toggleFavorite(item) },
            onClick = { vm.playFavorite(item) }
        )
        if (state.playlists.isNotEmpty()) {
            IconButton(
                onClick = { showPlaylistPicker = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to list", tint = EnigmaPink)
            }
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    state.playlists.forEach { pl ->
                        TextButton(
                            onClick = {
                                vm.addToPlaylist(pl.id, item)
                                showPlaylistPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(pl.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Close") }
            }
        )
    }
}
