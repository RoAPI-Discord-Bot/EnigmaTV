package com.enigma.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.ContinueWatchingStore
import com.enigma.tv.data.StreamSources
import com.enigma.tv.data.TmdbRepository
import com.enigma.tv.data.TvItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvUiState(
    val loading: Boolean = true,
    val continueWatching: List<ContinueWatchingEntry> = emptyList(),
    val trending: List<TvItem> = emptyList(),
    val popular: List<TvItem> = emptyList(),
    val onTheAir: List<TvItem> = emptyList(),
    val searchResults: List<TvItem>? = null,
    val error: String? = null,
    val playerVisible: Boolean = false,
    val playerTitle: String = "",
    val playerUrl: String = "",
    val sourceIndex: Int = 0,
    val sourceLabel: String = "",
    val currentShowId: Int? = null,
    val seasons: List<Int> = emptyList(),
    val episodes: List<Pair<Int, String>> = emptyList(),
    val selectedSeason: Int = 1,
    val selectedEpisode: Int = 1
)

class TvViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TmdbRepository()
    private val cwStore = ContinueWatchingStore(application)
    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state.asStateFlow()

    val continueWatching = cwStore.entries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        loadHome()
        viewModelScope.launch {
            continueWatching.collect { cw ->
                _state.update { it.copy(continueWatching = cw) }
            }
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, searchResults = null) }
            try {
                _state.update {
                    it.copy(
                        loading = false,
                        trending = repo.trendingTv(),
                        popular = repo.popularTv(),
                        onTheAir = repo.onTheAirTv()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "FAILED TO FETCH SHOWS") }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val results = repo.searchTv(query)
                _state.update { it.copy(loading = false, searchResults = results) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "SEARCH_ERROR") }
            }
        }
    }

    fun selectShow(id: Int, name: String, resumeSeason: Int? = null, resumeEpisode: Int? = null) {
        viewModelScope.launch {
            val startSeason = resumeSeason ?: 1
            val startEpisode = resumeEpisode ?: 1
            _state.update {
                it.copy(
                    playerVisible = true,
                    playerTitle = name,
                    currentShowId = id,
                    sourceIndex = 0,
                    selectedSeason = startSeason,
                    selectedEpisode = startEpisode
                )
            }
            try {
                val detail = repo.tvDetail(id)
                val seasons = detail.seasons.filter { it.seasonNumber > 0 }.map { it.seasonNumber }
                val poster = detail.posterPath?.let { p -> "https://image.tmdb.org/t/p/w200$p" } ?: ""
                cwStore.addOrUpdate(
                    ContinueWatchingEntry(
                        id = id,
                        name = name,
                        poster = poster,
                        season = startSeason,
                        episode = startEpisode
                    )
                )
                if (seasons.isNotEmpty()) {
                    val season = if (startSeason in seasons) startSeason else seasons.first()
                    loadEpisodes(id, season, startEpisode)
                    _state.update { it.copy(seasons = seasons, selectedSeason = season) }
                } else {
                    playCurrentEpisode()
                }
            } catch (e: Exception) {
                playCurrentEpisode()
            }
        }
    }

    fun loadEpisodes(showId: Int, season: Int, resumeEpisode: Int? = null) {
        viewModelScope.launch {
            _state.update { it.copy(selectedSeason = season) }
            try {
                val eps = repo.tvSeason(showId, season)
                val episodeList = eps.map { it.episodeNumber to it.name }
                val episode = resumeEpisode?.takeIf { n ->
                    episodeList.any { it.first == n }
                } ?: episodeList.firstOrNull()?.first ?: 1
                _state.update {
                    it.copy(episodes = episodeList, selectedEpisode = episode)
                }
                playCurrentEpisode()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        episodes = listOf((resumeEpisode ?: 1) to "Episode ${resumeEpisode ?: 1}"),
                        selectedEpisode = resumeEpisode ?: 1
                    )
                }
                playCurrentEpisode()
            }
        }
    }

    fun onSeasonChange(season: Int) {
        val showId = _state.value.currentShowId ?: return
        loadEpisodes(showId, season)
    }

    fun onEpisodeChange(episode: Int) {
        _state.update { it.copy(selectedEpisode = episode) }
        playCurrentEpisode()
    }

    fun playCurrentEpisode() {
        val s = _state.value
        val showId = s.currentShowId ?: return
        val (name, url) = StreamSources.tvUrl(s.sourceIndex, showId, s.selectedSeason, s.selectedEpisode)
        _state.update {
            it.copy(
                playerUrl = url,
                sourceLabel = "$name (${(s.sourceIndex % StreamSources.tvSources.size) + 1}/${StreamSources.tvSources.size})"
            )
        }
        viewModelScope.launch {
            cwStore.updateProgress(showId, s.selectedSeason, s.selectedEpisode)
        }
    }

    fun nextSource() {
        _state.update { it.copy(sourceIndex = it.sourceIndex + 1) }
        playCurrentEpisode()
    }

    fun closePlayer() {
        _state.update { it.copy(playerVisible = false, playerUrl = "") }
    }
}
