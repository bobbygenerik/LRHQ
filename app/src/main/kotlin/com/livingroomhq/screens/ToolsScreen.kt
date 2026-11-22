package com.livingroomhq.screens

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.components.linkLeftEdgeToSidebar
import com.livingroomhq.components.LocalContentFocusRequester
import com.livingroomhq.core.data.model.LaunchableApp
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.ModalGlassPanel
import com.livingroomhq.core.ui.components.ModalTitle
import com.livingroomhq.core.ui.components.tvInitialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.zonePadding
import com.livingroomhq.core.ui.components.EmptyStatePanel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone
import com.livingroomhq.screens.tools.ToolsViewModel
import com.livingroomhq.ui.LocalSnackbarController
import com.livingroomhq.core.ui.theme.hqAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val COLUMNS = 5
private const val ARM_LAUNCH_MS = 2_500L
private const val ICON_BITMAP_PX = 144
private val AppCardShape = RoundedCornerShape(HqDimens.CornerMd)
private val appIconCache = ConcurrentHashMap<String, ImageBitmap>()

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ToolsScreen(
    nav: LauncherNavController,
    viewModel: ToolsViewModel = viewModel(
        factory = run {
            val app = LocalContext.current.applicationContext as HqApplication
            ToolsViewModel.factory(app.installedApps, app.prefs)
        },
    ),
) {
    val accent = hqAccent()
    val context = LocalContext.current
    val contentFocus = LocalContentFocusRequester.current
    val snackbar = LocalSnackbarController.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var armedPackage by remember { mutableStateOf<String?>(null) }
    var armedAt by remember { mutableLongStateOf(0L) }

    fun disarmLaunch() {
        armedPackage = null
        armedAt = 0L
    }

    LaunchedEffect(state.hostResumeTick) {
        if (state.hostResumeTick > 0) disarmLaunch()
    }

    val apps = remember { mutableStateListOf<LaunchableApp>() }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var menuPackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.detected, state.savedOrder) {
        if (movingPackage == null && apps.toList() != state.apps) {
            apps.clear()
            apps.addAll(state.apps)
        }
    }

    val gridState = rememberLazyGridState()
    val moverFocus = remember { FocusRequester() }
    val firstItemFocusRequester = contentFocus ?: remember { FocusRequester() }
    val cardRequesters = remember { HashMap<String, FocusRequester>() }
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    var initialFocusGranted by remember { mutableStateOf(false) }
    var moveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(moveTick, movingPackage) {
        val pkg = movingPackage ?: return@LaunchedEffect
        val idx = apps.indexOfFirst { it.packageName == pkg }
        if (idx >= 0) runCatching { gridState.scrollToItem(idx) } // ensure the item is composed
        runCatching { moverFocus.requestFocus() }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && !initialFocusGranted) {
            initialFocusGranted = true
            withFrameNanos { }
            val targetPkg = focusedPackage ?: apps.firstOrNull()?.packageName
            val restored = targetPkg?.let { pkg ->
                runCatching { cardRequesters[pkg]?.requestFocus() }.isSuccess
            } ?: false
            if (!restored) {
                runCatching { firstItemFocusRequester.requestFocus() }
            }
        }
    }

    /** Dismiss the action menu after the current key event finishes composing. */
    fun dismissMenuAfterKey(restorePackage: String? = menuPackage) {
        scope.launch {
            withFrameNanos { }
            menuPackage = null
            if (restorePackage != null) {
                withFrameNanos { }
                val restored = runCatching { cardRequesters[restorePackage]?.requestFocus() }.isSuccess
                if (!restored) {
                    runCatching { firstItemFocusRequester.requestFocus() }
                }
            }
        }
    }

    fun moveBy(delta: Int) {
        val from = apps.indexOfFirst { it.packageName == movingPackage }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, apps.lastIndex)
        if (to == from) return
        apps.add(to, apps.removeAt(from))
        moveTick++
    }

    fun commitMove() {
        val pkg = movingPackage
        viewModel.saveOrder(apps.map { it.packageName })
        movingPackage = null
        if (pkg != null) {
            focusedPackage = pkg
            scope.launch {
                withFrameNanos { }
                runCatching { cardRequesters[pkg]?.requestFocus() }
            }
        }
    }

    fun cancelMove() {
        val pkg = movingPackage
        movingPackage = null
        if (apps.toList() != state.apps) {
            apps.clear()
            apps.addAll(state.apps)
        }
        if (pkg != null) {
            focusedPackage = pkg
            scope.launch {
                val idx = apps.indexOfFirst { it.packageName == pkg }
                if (idx >= 0) runCatching { gridState.scrollToItem(idx) }
                withFrameNanos { }
                runCatching { cardRequesters[pkg]?.requestFocus() }
            }
        }
    }

    fun openApp(packageName: String) {
        if (!viewModel.canLaunch()) return
        disarmLaunch()
        viewModel.launch(packageName, context)
    }

    // Single OK opens, like every other launcher. Phantom relaunches after returning
    // from an app are already blocked by InstalledAppsRepository.canLaunch() guards.
    @Suppress("UNUSED_PARAMETER")
    fun requestLaunch(packageName: String, label: String) {
        if (!viewModel.canLaunch()) return
        openApp(packageName)
    }

    BackHandler(enabled = menuPackage != null) {
        val restorePkg = menuPackage
        menuPackage = null
        if (restorePkg != null) {
            scope.launch {
                withFrameNanos { }
                runCatching { cardRequesters[restorePkg]?.requestFocus() }
            }
        }
    }
    BackHandler(enabled = movingPackage != null) { cancelMove() }

    // Do not set focusProperties.enter to a LazyGrid item FocusRequester — if that
    // item is scrolled off-screen the requester is detached and OK/focus-search
    // crashes the process (Shield then restarts the home activity on Home).
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zonePadding(),
        ) {
            Text("Apps", style = HqType.Title)
            // Only explain controls while in move mode, where they aren't obvious.
            if (movingPackage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "D-pad to position · OK to place · Back to cancel",
                    style = HqType.Label.copy(color = accent),
                )
            }
            Spacer(Modifier.height(16.dp))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyStatePanel(
                        title = if (state.isLoading) "Loading apps…" else "No apps found",
                        message = if (state.isLoading) "Scanning installed apps on this device." else "Install apps from the Play Store, then return here to launch and organize them.",
                        icon = Icons.Default.Apps,
                        actionLabel = if (state.isLoading) null else "Open Play Store",
                        onAction = {
                            context.startActivity(
                                Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier
                            .tvInitialFocus(firstItemFocusRequester)
                            .linkLeftEdgeToSidebar()
                            .focusable(),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    // Horizontal inset so focus-scaled edge cards don't clip.
                    contentPadding = PaddingValues(
                        start = HqDimens.GridEdgeInset,
                        end = HqDimens.GridEdgeInset,
                        top = HqDimens.GridEdgeInset,
                        bottom = 36.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = apps,
                        key = { _, app -> app.packageName },
                        contentType = { _, _ -> "app_card" },
                    ) { index, entry ->
                        val isMoving = entry.packageName == movingPackage
                        val cardRequester = remember(entry.packageName) {
                            cardRequesters.getOrPut(entry.packageName) { FocusRequester() }
                        }
                        val isPrimaryContentTarget =
                            entry.packageName == (focusedPackage ?: apps.firstOrNull()?.packageName)
                        AppCard(
                            entry = entry,
                            accent = accent,
                            isMoving = isMoving,
                            isArmed = armedPackage == entry.packageName,
                            packageManagerIcon = {
                                runCatching {
                                    context.packageManager.getApplicationIcon(entry.packageName)
                                }.getOrNull()
                            },
                            onClick = { requestLaunch(entry.packageName, entry.label) },
                            onLongClick = { menuPackage = entry.packageName },
                            onFocused = {
                                focusedPackage = entry.packageName
                                initialFocusGranted = true
                            },
                            onDisarm = {
                                if (armedPackage == entry.packageName) disarmLaunch()
                            },
                            moveModifier = when {
                                isMoving -> Modifier
                                    .focusRequester(moverFocus)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                                        when (ev.key) {
                                            Key.DirectionLeft -> moveBy(-1)
                                            Key.DirectionRight -> moveBy(1)
                                            Key.DirectionUp -> moveBy(-COLUMNS)
                                            Key.DirectionDown -> moveBy(COLUMNS)
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> commitMove()
                                            else -> return@onPreviewKeyEvent false
                                        }
                                        true
                                    }
                                else -> Modifier
                            },
                            modifier = Modifier
                                .focusRequester(cardRequester)
                                .then(
                                    if (isPrimaryContentTarget) {
                                        Modifier.focusRequester(firstItemFocusRequester)
                                    } else Modifier
                                )
                                .then(
                                    if (index < COLUMNS && !isMoving) {
                                        Modifier.focusProperties { up = FocusRequester.Cancel }
                                    } else Modifier
                                )
                                .then(
                                    if (index % COLUMNS == 0) {
                                        Modifier.linkLeftEdgeToSidebar()
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        }

        menuPackage?.let { pkg ->
            val entry = apps.firstOrNull { it.packageName == pkg }
            if (entry != null) {
                AppActionMenu(
                    label = entry.label,
                    onOpen = {
                        disarmLaunch()
                        openApp(pkg)
                        dismissMenuAfterKey(restorePackage = pkg)
                    },
                    onSettings = {
                        disarmLaunch()
                        viewModel.openAppSettings(pkg)
                        dismissMenuAfterKey(restorePackage = pkg)
                    },
                    onMove = {
                        disarmLaunch()
                        movingPackage = pkg
                        moveTick++
                        dismissMenuAfterKey(restorePackage = null)
                    },
                )
            }
        }
    }
}

@Composable
private fun AppCard(
    entry: LaunchableApp,
    accent: androidx.compose.ui.graphics.Color,
    isMoving: Boolean,
    isArmed: Boolean,
    packageManagerIcon: suspend () -> Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    onDisarm: () -> Unit,
    moveModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .height(148.dp)
        .onFocusChanged {
            if (it.isFocused) {
                onFocused()
            } else {
                onDisarm()
            }
        }
        .then(
            when {
                isMoving -> Modifier.border(2.dp, accent, AppCardShape)
                isArmed -> Modifier.border(2.dp, accent.copy(alpha = 0.85f), AppCardShape)
                else -> Modifier
            },
        )
        .then(moveModifier)
        .then(modifier)

    FocusableGlassCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = cardModifier,
        cornerRadius = HqDimens.CornerMd,
        contentPadding = PaddingValues(12.dp),
        sheenOnFocus = false,
    ) { focused ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(AppCardShape)
                    .background(HqColors.IconWell),
                contentAlignment = Alignment.Center,
            ) {
                var appIcon by remember(entry.packageName) {
                    mutableStateOf<ImageBitmap?>(appIconCache[entry.packageName])
                }
                LaunchedEffect(entry.packageName) {
                    if (appIcon == null) {
                        val loaded = withContext(Dispatchers.IO) {
                            runCatching {
                                packageManagerIcon()
                                    ?.toBitmap(width = ICON_BITMAP_PX, height = ICON_BITMAP_PX)
                                    ?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (loaded != null) {
                            appIconCache[entry.packageName] = loaded
                            appIcon = loaded
                        }
                    }
                }

                val iconBitmap = appIcon
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = entry.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = if (focused) accent else HqColors.TextTertiary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = entry.label,
                style = HqType.CardTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AppActionMenu(
    label: String,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onMove: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    var waitingForLongPressRelease by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HqColors.Scrim)
            .onPreviewKeyEvent { event ->
                if (!waitingForLongPressRelease || !event.key.isCenterKey()) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyUp) {
                    waitingForLongPressRelease = false
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        ModalGlassPanel(modifier = Modifier.width(320.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModalTitle(label)
                MenuRow("Open", Modifier.focusRequester(firstFocus), onOpen)
                MenuRow("App settings", onClick = onSettings)
                MenuRow("Move", onClick = onMove)
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = hqAccent()
    FocusableGlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        cornerRadius = HqDimens.CornerSm,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = label,
                style = HqType.CardTitle.copy(
                    color = if (focused) accent else HqColors.TextPrimary,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun Key.isCenterKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
