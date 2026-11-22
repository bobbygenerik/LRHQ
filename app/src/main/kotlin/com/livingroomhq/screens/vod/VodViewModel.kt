package com.livingroomhq.screens.vod

import android.content.Context
import android.util.Log
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
import com.livingroomhq.ui.UiMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    /** Search bar is open (chip selected). Browsing continues normally until a query is typed. */
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    /** True while a query is applied: the category lists above hold search results, not the catalog. */
    val showingSearchResults: Boolean = false,
) {
    /** Empty catalog. Never true while searching; "no results" is shown inside the search UI instead. */
    val isEmpty: Boolean
        get() = !searchActive && when (selectedTab) {
            VodTab.MOVIES -> movieCategories.isEmpty() || movieCategories.all { it.items.isEmpty() }
            VodTab.SERIES -> seriesCategories.isEmpty() || seriesCategories.all { it.series.isEmpty() }
        }
}

@OptIn(FlowPreview::class)
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
    private val _searchActive = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
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

    // Debounced so typing on a remote/IME doesn't re-scan the catalog per keystroke;
    // the scan itself runs off the main thread.
    private val searchResultsFlow = combine(
        _searchQuery.debounce(250).distinctUntilChanged(),
        vodRepository.categories,
        vodRepository.seriesCategories,
    ) { query, movies, series ->
        VodSearch.search(query, movies, series)
    }.flowOn(Dispatchers.Default)

    private data class SearchData(
        val active: Boolean,
        val query: String,
        val results: VodSearch.Results?,
    )

    private val searchFlow = combine(_searchActive, _searchQuery, searchResultsFlow) { active, query, results ->
        // While typing, keep showing the previous query's results until the debounced scan
        // catches up, rather than flashing the full catalog between keystrokes.
        val applied = results.takeIf {
            active &&
                VodSearch.normalize(query).length >= VodSearch.MIN_QUERY_LENGTH &&
                VodSearch.normalize(it.query).length >= VodSearch.MIN_QUERY_LENGTH
        }
        SearchData(active, query, applied)
    }

    val uiState: StateFlow<VodUiState> = combine(
        catalogFlow,
        focusDetailFlow,
        searchFlow,
    ) { catalog, focusDetail, search ->
        val results = search.results
        val movies = if (results != null) {
            listOfNotNull(
                results.movies.takeIf { it.isNotEmpty() }?.let {
                    VodCategory(id = "search_movies", name = "Movies matching \u201c${results.query.trim()}\u201d", items = it)
                },
            )
        } else {
            catalog.movies
        }
        val series = if (results != null) {
            listOfNotNull(
                results.series.takeIf { it.isNotEmpty() }?.let {
                    SeriesCategory(id = "search_series", name = "Shows matching \u201c${results.query.trim()}\u201d", series = it)
                },
            )
        } else {
            catalog.series
        }
        val fallbackMovie = movies.firstOrNull()?.items?.firstOrNull()
        val fallbackSeries = series.firstOrNull()?.series?.firstOrNull()
        // While results are showing, keep the billboard on a title that is actually in them.
        val focusedMovie = focusDetail.focusedMovie
            ?.takeIf { results == null || results.movies.any { m -> m.id == it.id } }
        val focusedSeries = focusDetail.focusedSeries
            ?.takeIf { results == null || results.series.any { s -> s.id == it.id } }

        VodUiState(
            selectedTab = catalog.tab,
            movieCategories = movies,
            seriesCategories = series,
            focusedMovie = focusedMovie ?: fallbackMovie,
            focusedSeries = focusedSeries ?: fallbackSeries,
            selectedSeriesDetail = focusDetail.detail,
            selectedSeason = focusDetail.season,
            isLoading = catalog.loading,
            isLoadingDetail = focusDetail.loadingDetail,
            searchActive = search.active,
            searchQuery = search.query,
            showingSearchResults = results != null,
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

    fun openSearch() {
        _searchActive.value = true
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun closeSearch() {
        _searchActive.value = false
        _searchQuery.value = ""
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
                Log.i("VodViewModel", "onSeriesClicked: loading '${series.title}' (seriesId=${series.seriesId})")
                val detail = vodRepository.getSeriesDetail(series.seriesId)
                if (detail != null && detail.seasons.isNotEmpty()) {
                    _selectedSeriesDetail.value = detail
                    val firstSeason = detail.seasons.firstOrNull() ?: 1
                    _selectedSeason.value = firstSeason
                } else {
                    Log.w("VodViewModel", "onSeriesClicked: no episodes returned for '${series.title}' (seriesId=${series.seriesId})")
                    UiMessages.post("No episodes available for ${series.title}")
                }
            } catch (e: Exception) {
                Log.e("VodViewModel", "onSeriesClicked exception for '${series.title}'", e)
                UiMessages.post("Unable to load episodes: ${e.localizedMessage ?: "Unknown error"}")
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
