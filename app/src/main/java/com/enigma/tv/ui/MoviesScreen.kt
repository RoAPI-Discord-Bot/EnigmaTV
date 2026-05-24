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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.tv.data.MovieItem
import com.enigma.tv.ui.theme.MovieAccent

@Composable
fun MoviesScreen(viewModel: MoviesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CineHeader(
                logo = "CINEFREE",
                accent = MovieAccent,
                placeholder = "Search movies...",
                query = query,
                onQueryChange = { query = it },
                onSearch = { viewModel.search(query) }
            )

            when {
                state.loading -> LoadingState("FETCHING_RECOMMENDATIONS...")
                state.error != null -> Text(
                    text = state.error!!,
                    color = Color(0xFF661111),
                    modifier = Modifier.padding(40.dp)
                )
                else -> ScrollableContent {
                    val movies = state.searchResults
                    if (movies != null) {
                        ContentSection("🔍 Results") {
                            PosterRow(movies.take(12).map { movieCard(it, viewModel) })
                        }
                    } else {
                        ContentSection("🔥 Trending This Week") {
                            PosterRow(state.trending.take(8).map { movieCard(it, viewModel) })
                        }
                        ContentSection("⭐ Popular Movies") {
                            PosterRow(state.popular.take(8).map { movieCard(it, viewModel) })
                        }
                    }
                }
            }
        }

        WebViewPlayer(
            visible = state.playerVisible,
            title = state.playerTitle,
            url = state.playerUrl,
            accent = MovieAccent,
            sourceLabel = state.sourceLabel,
            onClose = { viewModel.closePlayer() },
            onNextSource = { viewModel.nextSourceForCurrent() }
        )
    }
}

private fun movieCard(movie: MovieItem, viewModel: MoviesViewModel): @Composable () -> Unit = {
    {
        PosterCard(
            title = "${movie.title} (${movie.year})",
            posterUrl = movie.posterUrl,
            accent = MovieAccent,
            onClick = { viewModel.playMovieWithId(movie.id, movie.title) }
        )
    }
}
