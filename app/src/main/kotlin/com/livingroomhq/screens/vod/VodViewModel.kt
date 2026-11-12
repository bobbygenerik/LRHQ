package com.livingroomhq.screens.vod

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livingroomhq.core.data.model.EpisodeItem
import com.livingroomhq.core.data.model.SeriesCategory
import com.livingroomhq.core.data.model.SeriesDetail
import com.livingroomhq.core.data.model.SeriesItem
import com.livingroomhq.core.data.model.VodCategory
import com.livingroomhq.core.data.model.VodItem
import com.livingroomhq.core.data.repo.VodRepository
import com.livingroomhq.player.ChannelPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VodTab {
    MOVIES,
    SERIES,
}

data class VodUiState(
    val selectedTab: VodTab = VodTab.MOVIES,
    val movieCategories: List<VodCategory> = emptyList(),
    val seriesCategories: List<SeriesCategory> = emptyList(),
    val focusedMovie: VodItem? = null,
    val focusedSeries: SeriesItem? = null,
    val selectedSeriesDetail: SeriesDetail? = null,
    val selectedSeason: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingDetail: Boolean = false,
) {
    val isEmpty: Boolean
        get() = when (selectedTab) {
            VodTab.MOVIES -> movieCategories.isEmpty() || movieCategories.all { it.items.isEmpty() }
            VodTab.SERIES -> seriesCategories.isEmpty() || seriesCategories.all { it.series.isEmpty() }
        }
}

class VodViewModel(
    private val vodRepository: VodRepository,
    private val focusDebounceMs: Long = 160L,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(VodTab.MOVIES)
    private val _userFocusedMovie = MutableStateFlow<VodItem?>(null)
    private val _userFocusedSeries = MutableStateFlow<SeriesItem?>(null)
    private val _selectedSeriesDetail = MutableStateFlow<SeriesDetail?>(null)
    private val _selectedSeason = MutableStateFlow(1)
    private val _isLoadingDetail = MutableStateFlow(false)
    private var movieFocusJob: Job? = null
    private var seriesFocusJob: Job? = null

    private data class CatalogData(
        val tab: VodTab,
        val movies: List<VodCategory>,
        val series: List<SeriesCategory>,
        val loading: Boolean,
    )

    private data class FocusDetailData(
        val focusedMovie: VodItem?,
        val focusedSeries: SeriesItem?,
        val detail: SeriesDetail?,
        val season: Int,
        val loadingDetail: Boolean,
    )

    private val catalogFlow = combine(
        _selectedTab,
        vodRepository.categories,
        vodRepository.seriesCategories,
        vodRepository.isLoading,
    ) { tab, movies, series, loading ->
        CatalogData(tab, movies, series, loading)
    }

    private val focusDetailFlow = combine(
        _userFocusedMovie,
        _userFocusedSeries,
        _selectedSeriesDetail,
        _selectedSeason,
        _isLoadingDetail,
    ) { movie, series, detail, season, loadingDetail ->
        FocusDetailData(movie, series, detail, season, loadingDetail)
    }

    val uiState: StateFlow<VodUiState> = combine(
        catalogFlow,
        focusDetailFlow,
    ) { catalog, focusDetail ->
        val fallbackMovie = catalog.movies.firstOrNull()?.items?.firstOrNull()
        val fallbackSeries = catalog.series.firstOrNull()?.series?.firstOrNull()

        VodUiState(
            selectedTab = catalog.tab,
            movieCategories = catalog.movies,
            seriesCategories = catalog.series,
            focusedMovie = focusDetail.focusedMovie ?: fallbackMovie,
            focusedSeries = focusDetail.focusedSeries ?: fallbackSeries,
            selectedSeriesDetail = focusDetail.detail,
            selectedSeason = focusDetail.season,
            isLoading = catalog.loading,
            isLoadingDetail = focusDetail.loadingDetail,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // Seed from the repository's current (eagerly warmed) catalog so the first frame isn't blank.
        VodUiState(
            movieCategories = vodRepository.categories.value,
            seriesCategories = vodRepository.seriesCategories.value,
            focusedMovie = vodRepository.categories.value.firstOrNull()?.items?.firstOrNull(),
            focusedSeries = vodRepository.seriesCategories.value.firstOrNull()?.series?.firstOrNull(),
            isLoading = vodRepository.isLoading.value,
        ),
    )

    fun setTab(tab: VodTab) {
        _selectedTab.value = tab
    }

    fun onMovieFocused(item: VodItem) {
        if (_userFocusedMovie.value?.id == item.id) return
        movieFocusJob?.cancel()
        if (_userFocusedMovie.value == null || focusDebounceMs <= 0L) {
            _userFocusedMovie.value = item
        } else {
            movieFocusJob = viewModelScope.launch {
                delay(focusDebounceMs)
                _userFocusedMovie.value = item
            }
        }
    }

    fun onSeriesFocused(item: SeriesItem) {
        if (_userFocusedSeries.value?.id == item.id) return
        seriesFocusJob?.cancel()
        if (_userFocusedSeries.value == null || focusDebounceMs <= 0L) {
            _userFocusedSeries.value = item
        } else {
            seriesFocusJob = viewModelScope.launch {
                delay(focusDebounceMs)
                _userFocusedSeries.value = item
            }
        }
    }

    fun onSeriesClicked(series: SeriesItem) {
        viewModelScope.launch {
            _isLoadingDetail.value = true
            try {
                val detail = vodRepository.getSeriesDetail(series.seriesId)
                if (detail != null) {
                    _selectedSeriesDetail.value = detail
                    val firstSeason = detail.seasons.firstOrNull() ?: 1
                    _selectedSeason.value = firstSeason
                }
            } finally {
                _isLoadingDetail.value = false
            }
        }
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
    }

    fun closeSeriesDetail() {
        _selectedSeriesDetail.value = null
    }

    fun launchMoviePlayback(
        context: Context,
        item: VodItem,
    ) {
        ChannelPlayer.launchVod(context, item)
    }

    fun launchEpisodePlayback(
        context: Context,
        series: SeriesItem,
        episode: EpisodeItem,
    ) {
        ChannelPlayer.launchEpisode(context, series, episode)
    }

    companion object {
        fun factory(vodRepository: VodRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VodViewModel(vodRepository) as T
                }
            }
    }
}
