package com.enigma.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.MovieItem
import com.enigma.tv.data.StreamSources
import com.enigma.tv.data.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoviesUiState(
    val loading: Boolean = true,
    val trending: List<MovieItem> = emptyList(),
    val popular: List<MovieItem> = emptyList(),
    val searchResults: List<MovieItem>? = null,
    val error: String? = null,
    val playerVisible: Boolean = false,
    val playerTitle: String = "",
    val playerUrl: String = "",
    val sourceIndex: Int = 0,
    val sourceLabel: String = ""
)

class MoviesViewModel : ViewModel() {
    private val repo = TmdbRepository()
    private val _state = MutableStateFlow(MoviesUiState())
    val state: StateFlow<MoviesUiState> = _state.asStateFlow()

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, searchResults = null) }
            try {
                val trending = repo.trendingMovies()
                val popular = repo.popularMovies()
                _state.update {
                    it.copy(loading = false, trending = trending, popular = popular)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "FAILED TO FETCH RECOMMENDATIONS") }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val results = repo.searchMovies(query)
                _state.update { it.copy(loading = false, searchResults = results) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "SEARCH_ERROR") }
            }
        }
    }

    fun playMovie(tmdbId: Int, title: String) {
        val (name, url) = StreamSources.movieUrl(0, tmdbId)
        _state.update {
            it.copy(
                playerVisible = true,
                playerTitle = title,
                playerUrl = url,
                sourceIndex = 0,
                sourceLabel = "$name (1/${StreamSources.movieSources.size})"
            )
        }
    }

    fun nextSource(tmdbId: Int) {
        val next = (_state.value.sourceIndex + 1) % StreamSources.movieSources.size
        val (name, url) = StreamSources.movieUrl(next, tmdbId)
        _state.update {
            it.copy(
                sourceIndex = next,
                playerUrl = url,
                sourceLabel = "$name (${next + 1}/${StreamSources.movieSources.size})"
            )
        }
    }

    fun closePlayer() {
        _state.update { it.copy(playerVisible = false, playerUrl = "") }
    }

    var currentMovieId: Int? = null
        private set

    fun playMovieWithId(tmdbId: Int, title: String) {
        currentMovieId = tmdbId
        playMovie(tmdbId, title)
    }

    fun nextSourceForCurrent() {
        currentMovieId?.let { nextSource(it) }
    }
}
