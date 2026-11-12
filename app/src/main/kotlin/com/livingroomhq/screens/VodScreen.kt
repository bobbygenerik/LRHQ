package com.livingroomhq.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.BackdropBlurTransformation
import com.livingroomhq.components.LocalContentFocusRequester
import com.livingroomhq.components.linkLeftEdgeToSidebar
import com.livingroomhq.components.restoreFocusOnReturn
import com.livingroomhq.navigation.LauncherFocusTarget
import com.livingroomhq.core.data.model.EpisodeItem
import com.livingroomhq.core.data.model.SeriesCategory
import com.livingroomhq.core.data.model.SeriesDetail
import com.livingroomhq.core.data.model.SeriesItem
import com.livingroomhq.core.data.model.VodCategory
import com.livingroomhq.core.data.model.VodItem
import com.livingroomhq.core.ui.components.EmptyStatePanel
import com.livingroomhq.core.ui.components.tvFocusBorder
import com.livingroomhq.core.ui.components.tvFocusScale
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.hqAccent
import com.livingroomhq.core.ui.theme.zonePadding
import com.livingroomhq.navigation.FullscreenFocusReturn
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone
import com.livingroomhq.screens.vod.VodTab
import com.livingroomhq.screens.vod.VodUiState
import com.livingroomhq.screens.vod.VodViewModel
import kotlinx.coroutines.launch

private val PosterCardShape = RoundedCornerShape(HqDimens.CornerMd)
private val RatingBadgeShape = RoundedCornerShape(4.dp)
private val PosterBottomScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
)
private val BackdropOverlayScrim = Brush.verticalGradient(
    colors = listOf(
        HqColors.Void.copy(alpha = 0.5f),
        HqColors.Void.copy(alpha = 0.85f),
        HqColors.Void.copy(alpha = 0.98f),
    ),
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodScreen(
    nav: LauncherNavController,
    focusReturn: FullscreenFocusReturn,
    viewModel: VodViewModel = viewModel(
        factory = run {
            val app = LocalContext.current.applicationContext as HqApplication
            VodViewModel.factory(app.vod)
        },
    ),
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab by remember { derivedStateOf { uiState.value.selectedTab } }
    val movieCategories by remember { derivedStateOf { uiState.value.movieCategories } }
    val seriesCategories by remember { derivedStateOf { uiState.value.seriesCategories } }
    val isEmpty by remember { derivedStateOf { uiState.value.isEmpty } }
    val isLoading by remember { derivedStateOf { uiState.value.isLoading } }
    val isLoadingDetail by remember { derivedStateOf { uiState.value.isLoadingDetail } }
    val selectedSeriesDetail by remember { derivedStateOf { uiState.value.selectedSeriesDetail } }
    val selectedSeason by remember { derivedStateOf { uiState.value.selectedSeason } }
    val contentFocus = LocalContentFocusRequester.current
    val tabItemFocus = contentFocus ?: remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scrollScope = rememberCoroutineScope()
    val movieColumnState = rememberLazyListState()
    val seriesColumnState = rememberLazyListState()
    val movieRailStates = remember(movieCategories) { List(movieCategories.size) { LazyListState() } }
    val seriesRailStates = remember(seriesCategories) { List(seriesCategories.size) { LazyListState() } }
    val movieRailFirstRequesters = remember(movieCategories) { List(movieCategories.size) { FocusRequester() } }
    val seriesRailFirstRequesters = remember(seriesCategories) { List(seriesCategories.size) { FocusRequester() } }
    val firstNonEmptyMovieCatIdx = remember(movieCategories) {
        movieCategories.indexOfFirst { it.items.isNotEmpty() }.coerceAtLeast(0)
    }
    val firstNonEmptySeriesCatIdx = remember(seriesCategories) {
        seriesCategories.indexOfFirst { it.series.isNotEmpty() }.coerceAtLeast(0)
    }
    val activeColumnState = if (selectedTab == VodTab.MOVIES) movieColumnState else seriesColumnState
    val activeFirstRailState = if (selectedTab == VodTab.MOVIES) {
        movieRailStates.getOrNull(firstNonEmptyMovieCatIdx)
    } else {
        seriesRailStates.getOrNull(firstNonEmptySeriesCatIdx)
    }
    val firstRailCardFocus = if (selectedTab == VodTab.MOVIES) {
        movieRailFirstRequesters.getOrNull(firstNonEmptyMovieCatIdx)
    } else {
        seriesRailFirstRequesters.getOrNull(firstNonEmptySeriesCatIdx)
    }
    val activeFirstLazyItemIndex = 1 + if (selectedTab == VodTab.MOVIES) firstNonEmptyMovieCatIdx else firstNonEmptySeriesCatIdx

    val onMovieFocused = remember(viewModel) { { item: VodItem -> viewModel.onMovieFocused(item) } }
    val onMovieClicked = remember(viewModel, context, focusReturn) {
        { item: VodItem ->
            focusReturn.arm(LauncherFocusTarget(Zone.VOD, "vod:movie:${item.id}"))
            viewModel.launchMoviePlayback(context, item)
        }
    }
    val onSeriesFocused = remember(viewModel) { { s: SeriesItem -> viewModel.onSeriesFocused(s) } }
    val onSeriesClicked = remember(viewModel) { { s: SeriesItem -> viewModel.onSeriesClicked(s) } }
    val onTabSelected = remember(viewModel) { { tab: VodTab -> viewModel.setTab(tab) } }

    LaunchedEffect(Unit) {
        repeat(6) {
            withFrameNanos { }
            if (runCatching { tabItemFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    BackHandler(enabled = selectedSeriesDetail != null) {
        viewModel.closeSeriesDetail()
    }

    if (isEmpty && isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zonePadding()
                .focusRequester(tabItemFocus)
                .focusable()
                .linkLeftEdgeToSidebar(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = hqAccent(),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Loading On-Demand Library...",
                    style = HqType.Headline,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Syncing movies, TV shows, and series categories from your provider",
                    style = HqType.Body,
                    color = HqColors.TextSecondary,
                )
            }
        }
        return
    }

    if (isEmpty && !isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zonePadding()
                .focusRequester(tabItemFocus)
                .focusable()
                .linkLeftEdgeToSidebar(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyStatePanel(
                title = if (selectedTab == VodTab.MOVIES) "No Movies Found" else "No TV Series Found",
                message = "Configure an IPTV playlist with movie or series categories in Settings to browse on-demand titles.",
                icon = if (selectedTab == VodTab.MOVIES) Icons.Default.Movie else Icons.Default.Tv,
                actionLabel = "Go to Settings",
                onAction = { nav.goTo(Zone.SETTINGS) },
            )
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        VodBackdropLayer(uiState = uiState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = HqDimens.SafeHorizontal + 68.dp,
                    end = HqDimens.SafeHorizontal,
                    top = HqDimens.SafeVertical,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "ON DEMAND",
                        style = HqType.Title,
                        fontWeight = FontWeight.Bold,
                    )
                    val subtitle = if (selectedTab == VodTab.MOVIES) {
                        "${movieCategories.size} Movie Categories • Full Library"
                    } else {
                        "${seriesCategories.size} Series Categories • TV Shows"
                    }
                    Text(
                        text = subtitle,
                        style = HqType.CardCaption,
                        color = HqColors.TextSecondary,
                    )
                }

                VodTabSwitcher(
                    selectedTab = selectedTab,
                    firstTabFocus = tabItemFocus,
                    onMoveDown = {
                        val hasContent = when (selectedTab) {
                            VodTab.MOVIES -> movieCategories.any { it.items.isNotEmpty() }
                            VodTab.SERIES -> seriesCategories.any { it.series.isNotEmpty() }
                        }
                        if (!hasContent) {
                            false
                        } else if (firstRailCardFocus != null && runCatching { firstRailCardFocus.requestFocus() }.isSuccess) {
                            true
                        } else if (runCatching { focusManager.moveFocus(FocusDirection.Down) }.getOrDefault(false)) {
                            true
                        } else {
                            scrollScope.launch {
                                activeColumnState.scrollToItem(activeFirstLazyItemIndex)
                                activeFirstRailState?.scrollToItem(0)
                                repeat(6) {
                                    withFrameNanos { }
                                    if (firstRailCardFocus != null && runCatching { firstRailCardFocus.requestFocus() }.isSuccess) {
                                        return@launch
                                    }
                                }
                                runCatching { focusManager.moveFocus(FocusDirection.Down) }
                            }
                            true
                        }
                    },
                    onTabSelected = onTabSelected,
                )
            }

            LazyColumn(
                state = activeColumnState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = HqDimens.SafeVertical),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "billboard", contentType = "billboard") {
                    VodBillboardSection(uiState = uiState)
                }

            when (selectedTab) {
                VodTab.MOVIES -> {
                    itemsIndexed(
                        items = movieCategories,
                        key = { _, cat -> "movie_${cat.id}" },
                        contentType = { _, _ -> "movie_rail" },
                    ) { catIdx, category ->
                        val isFirstRail = catIdx == firstNonEmptyMovieCatIdx
                        val prevCatIdx = remember(movieCategories, catIdx) {
                            (catIdx - 1 downTo 0).firstOrNull { movieCategories[it].items.isNotEmpty() }
                        }
                        val nextCatIdx = remember(movieCategories, catIdx) {
                            (catIdx + 1 until movieCategories.size).firstOrNull { movieCategories[it].items.isNotEmpty() }
                        }
                        VodCategoryRail(
                            category = category,
                            isFirstRail = isFirstRail,
                            listState = movieRailStates[catIdx],
                            firstItemFocus = movieRailFirstRequesters[catIdx],
                            tabFocusRequester = tabItemFocus,
                            focusReturn = focusReturn,
                            onMoveUpToPrevRail = prevCatIdx?.let { prevIdx ->
                                {
                                    handleVodRailVerticalNav(
                                        direction = FocusDirection.Up,
                                        focusManager = focusManager,
                                        scrollScope = scrollScope,
                                        columnState = movieColumnState,
                                        targetColumnIndex = 1 + prevIdx,
                                        targetRailState = movieRailStates[prevIdx],
                                        targetFirstFocus = movieRailFirstRequesters[prevIdx],
                                    )
                                }
                            },
                            onMoveDownToNextRail = nextCatIdx?.let { nextIdx ->
                                {
                                    handleVodRailVerticalNav(
                                        direction = FocusDirection.Down,
                                        focusManager = focusManager,
                                        scrollScope = scrollScope,
                                        columnState = movieColumnState,
                                        targetColumnIndex = 1 + nextIdx,
                                        targetRailState = movieRailStates[nextIdx],
                                        targetFirstFocus = movieRailFirstRequesters[nextIdx],
                                    )
                                }
                            },
                            onItemFocused = onMovieFocused,
                            onItemClicked = onMovieClicked,
                        )
                    }
                }
                VodTab.SERIES -> {
                    itemsIndexed(
                        items = seriesCategories,
                        key = { _, cat -> "series_${cat.id}" },
                        contentType = { _, _ -> "series_rail" },
                    ) { catIdx, category ->
                        val isFirstRail = catIdx == firstNonEmptySeriesCatIdx
                        val prevCatIdx = remember(seriesCategories, catIdx) {
                            (catIdx - 1 downTo 0).firstOrNull { seriesCategories[it].series.isNotEmpty() }
                        }
                        val nextCatIdx = remember(seriesCategories, catIdx) {
                            (catIdx + 1 until seriesCategories.size).firstOrNull { seriesCategories[it].series.isNotEmpty() }
                        }
                        SeriesCategoryRail(
                            category = category,
                            isFirstRail = isFirstRail,
                            listState = seriesRailStates[catIdx],
                            firstItemFocus = seriesRailFirstRequesters[catIdx],
                            tabFocusRequester = tabItemFocus,
                            focusReturn = focusReturn,
                            onMoveUpToPrevRail = prevCatIdx?.let { prevIdx ->
                                {
                                    handleVodRailVerticalNav(
                                        direction = FocusDirection.Up,
                                        focusManager = focusManager,
                                        scrollScope = scrollScope,
                                        columnState = seriesColumnState,
                                        targetColumnIndex = 1 + prevIdx,
                                        targetRailState = seriesRailStates[prevIdx],
                                        targetFirstFocus = seriesRailFirstRequesters[prevIdx],
                                    )
                                }
                            },
                            onMoveDownToNextRail = nextCatIdx?.let { nextIdx ->
                                {
                                    handleVodRailVerticalNav(
                                        direction = FocusDirection.Down,
                                        focusManager = focusManager,
                                        scrollScope = scrollScope,
                                        columnState = seriesColumnState,
                                        targetColumnIndex = 1 + nextIdx,
                                        targetRailState = seriesRailStates[nextIdx],
                                        targetFirstFocus = seriesRailFirstRequesters[nextIdx],
                                    )
                                }
                            },
                            onSeriesFocused = onSeriesFocused,
                            onSeriesClicked = onSeriesClicked,
                        )
                    }
                }
            }
            }
        }

        if (isLoadingDetail && selectedSeriesDetail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HqColors.Void.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = hqAccent(),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Loading Series Seasons & Episodes...",
                        style = HqType.Headline,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        val seriesDetail = selectedSeriesDetail
        if (seriesDetail != null) {
            SeriesDetailModal(
                detail = seriesDetail,
                selectedSeason = selectedSeason,
                focusReturn = focusReturn,
                onSeasonSelected = { season -> viewModel.selectSeason(season) },
                onEpisodeClicked = { episode ->
                    focusReturn.arm(LauncherFocusTarget(Zone.VOD, "vod:episode:${episode.id}"))
                    viewModel.launchEpisodePlayback(context, seriesDetail.series, episode)
                },
                onClose = { viewModel.closeSeriesDetail() },
            )
        }
    }
}

@Composable
private fun VodBackdropLayer(uiState: State<VodUiState>) {
    val context = LocalContext.current
    val backdropInfo by remember {
        derivedStateOf {
            val s = uiState.value
            when (s.selectedTab) {
                VodTab.MOVIES -> {
                    val m = s.focusedMovie
                    val url = m?.backdropUrl?.takeIf { it.isNotBlank() } ?: m?.posterUrl
                    val isPortrait = m != null && (m.backdropUrl.isNullOrBlank() || m.backdropUrl == m.posterUrl)
                    url to isPortrait
                }
                VodTab.SERIES -> {
                    val ser = s.focusedSeries
                    val url = ser?.backdropUrl?.takeIf { it.isNotBlank() } ?: ser?.posterUrl
                    val isPortrait = ser != null && (ser.backdropUrl.isNullOrBlank() || ser.backdropUrl == ser.posterUrl)
                    url to isPortrait
                }
            }
        }
    }
    val (activeBackdropUrl, isPortraitPoster) = backdropInfo
    if (!activeBackdropUrl.isNullOrBlank()) {
        val request = remember(context, activeBackdropUrl, isPortraitPoster) {
            ImageRequest.Builder(context)
                .data(activeBackdropUrl)
                .crossfade(240)
                .allowHardware(true)
                .apply {
                    if (isPortraitPoster) {
                        size(480, 270)
                        transformations(BackdropBlurTransformation(context, radius = 25f, sampling = 3f))
                    } else {
                        size(1280, 720)
                    }
                }
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPortraitPoster) {
                        Modifier.graphicsLayer {
                            scaleX = 1.15f
                            scaleY = 1.15f
                            alpha = 0.65f
                        }
                    } else Modifier
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackdropOverlayScrim),
        )
    }
}

@Composable
private fun VodBillboardSection(uiState: State<VodUiState>) {
    val selectedTab by remember { derivedStateOf { uiState.value.selectedTab } }
    when (selectedTab) {
        VodTab.MOVIES -> {
            val focusedMovie by remember { derivedStateOf { uiState.value.focusedMovie } }
            val movie = focusedMovie
            if (movie != null) {
                VodHeroBillboard(item = movie)
            } else {
                Spacer(Modifier.height(180.dp))
            }
        }
        VodTab.SERIES -> {
            val focusedSeries by remember { derivedStateOf { uiState.value.focusedSeries } }
            val series = focusedSeries
            if (series != null) {
                SeriesHeroBillboard(series = series)
            } else {
                Spacer(Modifier.height(180.dp))
            }
        }
    }
}

@Composable
private fun VodTabSwitcher(
    selectedTab: VodTab,
    firstTabFocus: FocusRequester,
    onMoveDown: () -> Boolean,
    onTabSelected: (VodTab) -> Unit,
) {
    val moviesFocus = remember { FocusRequester() }
    val seriesFocus = remember { FocusRequester() }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VodTabChip(
            title = "Movies",
            icon = Icons.Default.Movie,
            isSelected = selectedTab == VodTab.MOVIES,
            focusRequester = if (selectedTab == VodTab.MOVIES) firstTabFocus else moviesFocus,
            isLeadingEdge = true,
            onMoveDown = onMoveDown,
            onClick = { onTabSelected(VodTab.MOVIES) },
        )
        VodTabChip(
            title = "TV Shows",
            icon = Icons.Default.Tv,
            isSelected = selectedTab == VodTab.SERIES,
            focusRequester = if (selectedTab == VodTab.SERIES) firstTabFocus else seriesFocus,
            isLeadingEdge = false,
            onMoveDown = onMoveDown,
            onClick = { onTabSelected(VodTab.SERIES) },
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun VodTabChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    isLeadingEdge: Boolean,
    onMoveDown: () -> Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var centerDown by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val accent = hqAccent()

    Box(
        modifier = Modifier
            .then(if (isLeadingEdge) Modifier.linkLeftEdgeToSidebar() else Modifier)
            .focusProperties { up = FocusRequester.Cancel }
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (!state.isFocused) centerDown = false
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionDown -> {
                        if (event.type == KeyEventType.KeyDown) {
                            onMoveDown()
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyDown) {
                            centerDown = true
                            true
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (centerDown) {
                                centerDown = false
                                onClick()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { onClick() }
            .tvFocusScale(isFocused)
            .clip(shape)
            .background(
                when {
                    isSelected && isFocused -> accent
                    isSelected -> accent.copy(alpha = 0.85f)
                    isFocused -> HqColors.Slate
                    else -> HqColors.Slate.copy(alpha = 0.5f)
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) accent else HqColors.GlassStroke,
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected || isFocused) Color.White else HqColors.TextSecondary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = title,
                style = HqType.CardCaption,
                color = if (isSelected || isFocused) Color.White else HqColors.TextSecondary,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun VodHeroBillboard(
    item: VodItem,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(hqAccent().copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "VOD MOVIE",
                    style = HqType.CardCaption,
                    color = HqColors.OnAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }

            val rating = item.rating
            if (!rating.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .border(1.dp, hqAccent(), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = rating,
                        style = HqType.CardCaption,
                        color = hqAccent(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                }
            }

            if (item.year > 0) {
                Text(
                    text = "${item.year}",
                    style = HqType.CardCaption,
                    color = HqColors.TextSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (item.durationMinutes > 0) {
                Text(
                    text = "${item.durationMinutes} min",
                    style = HqType.CardCaption,
                    color = HqColors.TextSecondary,
                )
            }

            Text(
                text = "•   ${item.genre}",
                style = HqType.CardCaption,
                color = HqColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = item.title,
            style = HqType.Headline,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.description,
            style = HqType.Body,
            color = HqColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.75f),
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = hqAccent(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Press Select to watch now",
                style = HqType.CardCaption,
                color = hqAccent(),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SeriesHeroBillboard(
    series: SeriesItem,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(hqAccent().copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (series.categoryName.isNotBlank()) series.categoryName.uppercase() else "TV SERIES",
                    style = HqType.CardCaption,
                    color = HqColors.OnAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }

            val rating = series.rating
            if (!rating.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .border(1.dp, hqAccent(), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = rating,
                        style = HqType.CardCaption,
                        color = hqAccent(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                }
            }

            if (series.year > 0) {
                Text(
                    text = "${series.year}",
                    style = HqType.CardCaption,
                    color = HqColors.TextSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (series.genre.isNotBlank() && series.genre != series.categoryName) {
                Text(
                    text = "•   ${series.genre}",
                    style = HqType.CardCaption,
                    color = HqColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = series.title,
            style = HqType.Headline,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = series.plot.ifBlank { "Browse all seasons and episodes on-demand." },
            style = HqType.Body,
            color = HqColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.75f),
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = hqAccent(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Press Select to browse seasons & episodes",
                style = HqType.CardCaption,
                color = hqAccent(),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun VodCategoryRail(
    category: VodCategory,
    isFirstRail: Boolean,
    listState: LazyListState = rememberLazyListState(),
    firstItemFocus: FocusRequester,
    tabFocusRequester: FocusRequester,
    focusReturn: FullscreenFocusReturn? = null,
    onMoveUpToPrevRail: (() -> Boolean)? = null,
    onMoveDownToNextRail: (() -> Boolean)? = null,
    onItemFocused: (VodItem) -> Unit,
    onItemClicked: (VodItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = category.name,
                style = HqType.Headline,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${category.items.size}",
                style = HqType.CardCaption,
                color = HqColors.TextTertiary,
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 8.dp, top = 12.dp, bottom = 16.dp, end = 40.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(
                items = category.items,
                key = { itemIdx, item -> "${category.id}_${item.id}_$itemIdx" },
                contentType = { _, _ -> "vod_card" },
            ) { itemIdx, item ->
                val isFirstInRow = itemIdx == 0
                val itemRequester = if (isFirstInRow) firstItemFocus else remember(item.id) { FocusRequester() }

                VodCard(
                    item = item,
                    focusRequester = itemRequester,
                    focusReturn = focusReturn,
                    isLeadingEdge = isFirstInRow,
                    isFirstRail = isFirstRail,
                    tabFocusRequester = tabFocusRequester,
                    onMoveUpToPrevRail = onMoveUpToPrevRail,
                    onMoveDownToNextRail = onMoveDownToNextRail,
                    onFocused = { onItemFocused(item) },
                    onClicked = { onItemClicked(item) },
                )
            }
        }
    }
}

@Composable
private fun SeriesCategoryRail(
    category: SeriesCategory,
    isFirstRail: Boolean,
    listState: LazyListState = rememberLazyListState(),
    firstItemFocus: FocusRequester,
    tabFocusRequester: FocusRequester,
    focusReturn: FullscreenFocusReturn? = null,
    onMoveUpToPrevRail: (() -> Boolean)? = null,
    onMoveDownToNextRail: (() -> Boolean)? = null,
    onSeriesFocused: (SeriesItem) -> Unit,
    onSeriesClicked: (SeriesItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = category.name,
                style = HqType.Headline,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${category.series.size}",
                style = HqType.CardCaption,
                color = HqColors.TextTertiary,
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 8.dp, top = 12.dp, bottom = 16.dp, end = 40.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(
                items = category.series,
                key = { itemIdx, s -> "${category.id}_${s.id}_$itemIdx" },
                contentType = { _, _ -> "series_card" },
            ) { itemIdx, s ->
                val isFirstInRow = itemIdx == 0
                val itemRequester = if (isFirstInRow) firstItemFocus else remember(s.id) { FocusRequester() }

                SeriesCard(
                    series = s,
                    focusRequester = itemRequester,
                    isLeadingEdge = isFirstInRow,
                    isFirstRail = isFirstRail,
                    tabFocusRequester = tabFocusRequester,
                    onMoveUpToPrevRail = onMoveUpToPrevRail,
                    onMoveDownToNextRail = onMoveDownToNextRail,
                    onFocused = { onSeriesFocused(s) },
                    onClicked = { onSeriesClicked(s) },
                )
            }
        }
    }
}

private fun handleVodRailVerticalNav(
    direction: FocusDirection,
    focusManager: androidx.compose.ui.focus.FocusManager,
    scrollScope: kotlinx.coroutines.CoroutineScope,
    columnState: LazyListState,
    targetColumnIndex: Int,
    targetRailState: LazyListState?,
    targetFirstFocus: FocusRequester?,
): Boolean {
    if (runCatching { focusManager.moveFocus(direction) }.getOrDefault(false)) {
        return true
    }
    if (targetFirstFocus == null) return false
    scrollScope.launch {
        columnState.animateScrollToItem(targetColumnIndex)
        if (targetRailState != null &&
            !runCatching { targetFirstFocus.requestFocus() }.isSuccess &&
            (targetRailState.firstVisibleItemIndex > 0 || targetRailState.firstVisibleItemScrollOffset > 0)
        ) {
            targetRailState.scrollToItem(0)
        }
        repeat(6) {
            withFrameNanos { }
            if (runCatching { targetFirstFocus.requestFocus() }.isSuccess) {
                return@launch
            }
        }
        runCatching { focusManager.moveFocus(direction) }
    }
    return true
}

@Composable
private fun Modifier.vodCardChrome(
    focused: Boolean,
    shape: RoundedCornerShape,
): Modifier = this
    .tvFocusScale(focused)
    .clip(shape)
    .background(HqColors.Slate)
    .border(width = 1.dp, color = HqColors.GlassStroke, shape = shape)
    .tvFocusBorder(focused = focused, shape = shape, width = 2.5.dp)

@Composable
private fun VodCard(
    item: VodItem,
    focusRequester: FocusRequester,
    focusReturn: FullscreenFocusReturn? = null,
    isLeadingEdge: Boolean,
    isFirstRail: Boolean = false,
    tabFocusRequester: FocusRequester? = null,
    onMoveUpToPrevRail: (() -> Boolean)? = null,
    onMoveDownToNextRail: (() -> Boolean)? = null,
    onFocused: () -> Unit,
    onClicked: () -> Unit,
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var centerDown by remember { mutableStateOf(false) }
    val shape = PosterCardShape
    val accent = hqAccent()

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(225.dp)
            .then(
                if (focusReturn != null) {
                    Modifier.restoreFocusOnReturn(
                        focusReturn = focusReturn,
                        target = LauncherFocusTarget(Zone.VOD, "vod:movie:${item.id}"),
                        requester = focusRequester,
                    )
                } else Modifier
            )
            .then(if (isLeadingEdge) Modifier.linkLeftEdgeToSidebar() else Modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (!state.isFocused) {
                    centerDown = false
                } else {
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionUp -> {
                        if (event.type == KeyEventType.KeyDown) {
                            when {
                                isFirstRail && tabFocusRequester != null ->
                                    runCatching { tabFocusRequester.requestFocus() }.isSuccess
                                onMoveUpToPrevRail != null -> onMoveUpToPrevRail()
                                else -> false
                            }
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (event.type == KeyEventType.KeyDown && onMoveDownToNextRail != null) {
                            onMoveDownToNextRail()
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyDown) {
                            centerDown = true
                            true
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (centerDown) {
                                centerDown = false
                                onClicked()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { onClicked() }
            .vodCardChrome(isFocused, shape),
    ) {
        if (!item.posterUrl.isNullOrBlank()) {
            val posterRequest = remember(context, item.posterUrl) {
                ImageRequest.Builder(context)
                    .data(item.posterUrl)
                    .size(300, 450)
                    .crossfade(false)
                    .allowHardware(true)
                    .build()
            }
            AsyncImage(
                model = posterRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HqColors.Slate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = if (isFocused) accent else HqColors.TextSecondary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        if (!item.rating.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RatingBadgeShape)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = item.rating.orEmpty(),
                    style = HqType.CardCaption,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }

        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.08f)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(PosterBottomScrim)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column {
                Text(
                    text = item.title,
                    style = HqType.CardCaption,
                    color = if (isFocused) Color.White else HqColors.TextSecondary,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.year > 0) {
                    Text(
                        text = "${item.year}",
                        style = HqType.CardCaption,
                        color = HqColors.TextTertiary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesCard(
    series: SeriesItem,
    focusRequester: FocusRequester,
    isLeadingEdge: Boolean,
    isFirstRail: Boolean = false,
    tabFocusRequester: FocusRequester? = null,
    onMoveUpToPrevRail: (() -> Boolean)? = null,
    onMoveDownToNextRail: (() -> Boolean)? = null,
    onFocused: () -> Unit,
    onClicked: () -> Unit,
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var centerDown by remember { mutableStateOf(false) }
    val shape = PosterCardShape
    val accent = hqAccent()

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(225.dp)
            .then(if (isLeadingEdge) Modifier.linkLeftEdgeToSidebar() else Modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (!state.isFocused) {
                    centerDown = false
                } else {
                    onFocused()
                }
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionUp -> {
                        if (event.type == KeyEventType.KeyDown) {
                            when {
                                isFirstRail && tabFocusRequester != null ->
                                    runCatching { tabFocusRequester.requestFocus() }.isSuccess
                                onMoveUpToPrevRail != null -> onMoveUpToPrevRail()
                                else -> false
                            }
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (event.type == KeyEventType.KeyDown && onMoveDownToNextRail != null) {
                            onMoveDownToNextRail()
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyDown) {
                            centerDown = true
                            true
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (centerDown) {
                                centerDown = false
                                onClicked()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { onClicked() }
            .vodCardChrome(isFocused, shape),
    ) {
        if (!series.posterUrl.isNullOrBlank()) {
            val posterRequest = remember(context, series.posterUrl) {
                ImageRequest.Builder(context)
                    .data(series.posterUrl)
                    .size(300, 450)
                    .crossfade(false)
                    .allowHardware(true)
                    .build()
            }
            AsyncImage(
                model = posterRequest,
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HqColors.Slate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (isFocused) accent else HqColors.TextSecondary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        if (!series.rating.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RatingBadgeShape)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = series.rating.orEmpty(),
                    style = HqType.CardCaption,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }

        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.08f)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(PosterBottomScrim)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column {
                Text(
                    text = series.title,
                    style = HqType.CardCaption,
                    color = if (isFocused) Color.White else HqColors.TextSecondary,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (series.year > 0) {
                    Text(
                        text = "${series.year}",
                        style = HqType.CardCaption,
                        color = HqColors.TextTertiary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SeriesDetailModal(
    detail: SeriesDetail,
    selectedSeason: Int,
    focusReturn: FullscreenFocusReturn? = null,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClicked: (EpisodeItem) -> Unit,
    onClose: () -> Unit,
) {
    val series = detail.series
    val episodes = detail.episodesBySeason[selectedSeason].orEmpty()
    val firstSeasonFocus = remember { FocusRequester() }

    LaunchedEffect(detail.series.seriesId) {
        if (focusReturn?.returnEvent?.value?.target == null) {
            firstSeasonFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HqColors.Void.copy(alpha = 0.96f)),
    ) {
        if (!series.backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = series.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                HqColors.Void.copy(alpha = 0.70f),
                                HqColors.Void.copy(alpha = 0.92f),
                                HqColors.Void.copy(alpha = 0.99f),
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .zonePadding()
                .padding(top = 16.dp, bottom = HqDimens.SafeVertical),
        ) {
            // Header / Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = hqAccent(),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "TV SERIES",
                        style = HqType.CardCaption,
                        color = hqAccent(),
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HqColors.Slate.copy(alpha = 0.7f))
                        .clickable { onClose() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = HqColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Press Back to Return",
                        style = HqType.CardCaption,
                        color = HqColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Series Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Poster
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(195.dp)
                        .clip(RoundedCornerShape(HqDimens.CornerMd))
                        .background(HqColors.Slate)
                        .border(1.dp, HqColors.GlassStroke, RoundedCornerShape(HqDimens.CornerMd)),
                ) {
                    if (!series.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = series.posterUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = HqColors.TextSecondary,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center),
                        )
                    }
                }

                // Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (series.categoryName.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(hqAccent().copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = series.categoryName.uppercase(),
                                    style = HqType.CardCaption,
                                    color = HqColors.OnAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                )
                            }
                        }

                        if (!series.rating.isNullOrBlank()) {
                            Text(
                                text = "${series.rating} ★",
                                style = HqType.CardCaption,
                                color = hqAccent(),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (series.year > 0) {
                            Text(
                                text = "${series.year}",
                                style = HqType.CardCaption,
                                color = HqColors.TextSecondary,
                            )
                        }

                        Text(
                            text = "${detail.seasons.size} Seasons",
                            style = HqType.CardCaption,
                            color = HqColors.TextTertiary,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = series.title,
                        style = HqType.Title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = series.plot.ifBlank { "No synopsis available." },
                        style = HqType.Body,
                        color = HqColors.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Season Tabs
            Text(
                text = "SEASONS",
                style = HqType.CardCaption,
                color = HqColors.TextTertiary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer()
                    .focusGroup(),
            ) {
                itemsIndexed(detail.seasons) { idx, seasonNum ->
                    val isFirst = idx == 0
                    SeasonTabChip(
                        seasonNum = seasonNum,
                        isSelected = seasonNum == selectedSeason,
                        focusRequester = if (isFirst) firstSeasonFocus else remember { FocusRequester() },
                        onClick = { onSeasonSelected(seasonNum) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Episodes Rail
            Text(
                text = "SEASON $selectedSeason • ${episodes.size} EPISODES",
                style = HqType.CardCaption,
                color = HqColors.TextTertiary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp, end = 40.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer()
                    .focusGroup(),
            ) {
                itemsIndexed(episodes, key = { epIdx, ep -> "${ep.id}_$epIdx" }) { _, episode ->
                    val epRequester = remember(episode.id) { FocusRequester() }
                    EpisodeCard(
                        episode = episode,
                        fallbackPosterUrl = series.posterUrl,
                        focusRequester = epRequester,
                        focusReturn = focusReturn,
                        onClicked = { onEpisodeClicked(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonTabChip(
    seasonNum: Int,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var centerDown by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val accent = hqAccent()

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (!state.isFocused) centerDown = false
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyDown) {
                            centerDown = true
                            true
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (centerDown) {
                                centerDown = false
                                onClick()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { onClick() }
            .tvFocusScale(isFocused)
            .clip(shape)
            .background(
                when {
                    isSelected && isFocused -> accent
                    isSelected -> accent.copy(alpha = 0.85f)
                    isFocused -> HqColors.Slate
                    else -> HqColors.Slate.copy(alpha = 0.5f)
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) accent else HqColors.GlassStroke,
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Season $seasonNum",
            style = HqType.CardCaption,
            color = if (isSelected || isFocused) Color.White else HqColors.TextSecondary,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    fallbackPosterUrl: String?,
    focusRequester: FocusRequester = remember { FocusRequester() },
    focusReturn: FullscreenFocusReturn? = null,
    onClicked: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var centerDown by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(HqDimens.CornerMd)
    val accent = hqAccent()
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(160.dp)
            .bringIntoViewRequester(bringIntoView)
            .then(
                if (focusReturn != null) {
                    Modifier.restoreFocusOnReturn(
                        focusReturn = focusReturn,
                        target = LauncherFocusTarget(Zone.VOD, "vod:episode:${episode.id}"),
                        requester = focusRequester,
                    )
                } else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (!state.isFocused) {
                    centerDown = false
                } else {
                    scope.launch { bringIntoView.bringIntoView() }
                }
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyDown) {
                            centerDown = true
                            true
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (centerDown) {
                                centerDown = false
                                onClicked()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { onClicked() }
            .vodCardChrome(isFocused, shape),
    ) {
        val imageUrl = episode.posterUrl ?: fallbackPosterUrl
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HqColors.Slate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (isFocused) accent else HqColors.TextSecondary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // Episode number badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = "EPISODE ${episode.episodeNum}",
                style = HqType.CardCaption,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            )
        }

        // Duration badge
        if (episode.durationMinutes > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "${episode.durationMinutes}m",
                    style = HqType.CardCaption,
                    color = HqColors.TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }

        // Play icon overlay when focused
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = HqColors.OnAccent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // Episode Title & Plot Gradient Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f)),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column {
                Text(
                    text = episode.title,
                    style = HqType.CardCaption,
                    color = if (isFocused) Color.White else HqColors.TextSecondary,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.plot.isNotBlank()) {
                    Text(
                        text = episode.plot,
                        style = HqType.CardCaption,
                        color = HqColors.TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
