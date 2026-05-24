package com.enigma.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.tv.data.TvItem
import com.enigma.tv.ui.theme.TvAccent

@Composable
fun TvScreen(viewModel: TvViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CineHeader(
                logo = "CINETV",
                accent = TvAccent,
                placeholder = "Search TV shows...",
                query = query,
                onQueryChange = { query = it },
                onSearch = { viewModel.search(query) }
            )

            when {
                state.loading -> LoadingState("FETCHING_SHOWS...")
                state.error != null -> Text(
                    text = state.error!!,
                    color = Color(0xFF661111),
                    modifier = Modifier.padding(40.dp)
                )
                else -> ScrollableContent {
                    val shows = state.searchResults
                    if (shows != null) {
                        ContentSection("🔍 Results for \"$query\"") {
                            PosterRow(shows.take(12).map { tvCard(it, viewModel) })
                        }
                    } else {
                        if (state.continueWatching.isNotEmpty()) {
                            ContentSection("▶ Continue Watching") {
                                PosterRow(state.continueWatching.take(6).map { entry ->
                                    {
                                        PosterCard(
                                            title = entry.name,
                                            posterUrl = entry.poster.ifBlank { null },
                                            accent = TvAccent,
                                            badge = "TV",
                                            subtitle = "S${entry.season}E${entry.episode}",
                                            onClick = {
                                                viewModel.selectShow(
                                                    entry.id,
                                                    entry.name,
                                                    entry.season,
                                                    entry.episode
                                                )
                                            }
                                        )
                                    }
                                })
                            }
                        }
                        ContentSection("🔥 Trending Shows") {
                            PosterRow(state.trending.take(8).map { tvCard(it, viewModel) })
                        }
                        ContentSection("📺 Popular Now") {
                            PosterRow(state.popular.take(8).map { tvCard(it, viewModel) })
                        }
                        ContentSection("🆕 On The Air") {
                            PosterRow(state.onTheAir.take(8).map { tvCard(it, viewModel) })
                        }
                    }
                }
            }
        }

        val tvControls = if (state.playerVisible && state.seasons.isNotEmpty()) {
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
            accent = TvAccent,
            sourceLabel = state.sourceLabel,
            onClose = { viewModel.closePlayer() },
            onNextSource = { viewModel.nextSource() },
            tvControls = tvControls
        )
    }
}

private fun tvCard(show: TvItem, viewModel: TvViewModel): @Composable () -> Unit = {
    {
        PosterCard(
            title = "${show.displayName} (${show.year})",
            posterUrl = show.posterUrl,
            accent = TvAccent,
            badge = "TV",
            onClick = { viewModel.selectShow(show.id, show.displayName) }
        )
    }
}
