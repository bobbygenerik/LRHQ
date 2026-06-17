package com.livingroomhq.screens

import android.graphics.drawable.Drawable
import coil.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.LaunchableApp
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COLUMNS = 4

@Composable
fun ToolsScreen(app: HqApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detected by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    val savedOrder by app.prefs.appOrder.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        detected = app.installedApps.launchableApps()
    }

    // Working list shown in the grid. Rebuilt from detected apps + saved order,
    // except while a move is in progress (so live repositioning isn't clobbered).
    val apps = remember { mutableStateListOf<LaunchableApp>() }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var menuPackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(detected, savedOrder) {
        if (movingPackage == null) {
            apps.clear()
            apps.addAll(mergeOrder(detected, savedOrder))
        }
    }

    val gridState = rememberLazyGridState()
    val moverFocus = remember { FocusRequester() }
    var moveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(moveTick, movingPackage) {
        val pkg = movingPackage ?: return@LaunchedEffect
        val idx = apps.indexOfFirst { it.packageName == pkg }
        if (idx >= 0) runCatching { gridState.scrollToItem(idx) } // ensure the item is composed
        runCatching { moverFocus.requestFocus() }
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
        scope.launch { app.prefs.setAppOrder(apps.map { it.packageName }) }
        movingPackage = null
    }

    fun cancelMove() {
        movingPackage = null
        apps.clear()
        apps.addAll(mergeOrder(detected, savedOrder))
    }

    BackHandler(enabled = menuPackage != null) { menuPackage = null }
    BackHandler(enabled = movingPackage != null) { cancelMove() }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text("APPS", style = HqType.Title)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (movingPackage != null) {
                    "D-pad to position · OK to place · Back to cancel"
                } else {
                    "Press OK to open · hold OK for more"
                },
                style = HqType.Label.copy(
                    color = if (movingPackage != null) HqColors.Accent else HqColors.TextSecondary,
                ),
            )
            Spacer(Modifier.height(16.dp))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No installed applications found", style = HqType.Body)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(apps, key = { it.packageName }) { entry ->
                        val isMoving = entry.packageName == movingPackage
                        AppCard(
                            entry = entry,
                            isMoving = isMoving,
                            packageManagerIcon = {
                                runCatching {
                                    context.packageManager.getApplicationIcon(entry.packageName)
                                }.getOrNull()
                            },
                            onClick = { app.installedApps.launch(entry.packageName) },
                            onLongClick = { menuPackage = entry.packageName },
                            moveModifier = if (isMoving) {
                                Modifier
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
                            } else {
                                Modifier
                            },
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
                        menuPackage = null
                        app.installedApps.launch(pkg)
                    },
                    onSettings = {
                        menuPackage = null
                        app.installedApps.openAppSettings(pkg)
                    },
                    onMove = {
                        menuPackage = null
                        movingPackage = pkg
                        moveTick++
                    },
                )
            }
        }
    }
}

@Composable
private fun AppCard(
    entry: LaunchableApp,
    isMoving: Boolean,
    packageManagerIcon: suspend () -> Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    moveModifier: Modifier,
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
        .then(
            if (isMoving) {
                Modifier.border(2.dp, HqColors.Accent, RoundedCornerShape(8.dp))
            } else {
                Modifier
            },
        )
        .then(moveModifier)

    FocusableGlassCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = cardModifier,
        cornerRadius = 8.dp,
        contentPadding = PaddingValues(12.dp),
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x11FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                var appIcon by remember(entry.packageName) { mutableStateOf<Drawable?>(null) }
                LaunchedEffect(entry.packageName) {
                    withContext(Dispatchers.IO) { appIcon = packageManagerIcon() }
                }

                if (appIcon != null) {
                    AsyncImage(
                        model = appIcon,
                        contentDescription = entry.label,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = if (focused) HqColors.Accent else HqColors.TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = entry.label,
                    style = HqType.Body.copy(
                        color = HqColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = if (entry.isTvApp) "TV APP" else "MOBILE APP",
                    style = HqType.Label.copy(
                        color = if (entry.isTvApp) HqColors.Accent else HqColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
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
            .background(Color(0xCC000000))
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
        Column(
            modifier = Modifier.widthIn(min = 260.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = HqType.Body.copy(color = HqColors.TextPrimary, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            MenuRow("Open", Modifier.focusRequester(firstFocus), onOpen)
            MenuRow("App settings", onClick = onSettings)
            MenuRow("Move", onClick = onMove)
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusableGlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        cornerRadius = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = label,
                style = HqType.Body.copy(
                    color = if (focused) HqColors.Accent else HqColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Detected apps reordered by the user's saved preference. Saved entries that are
 * still installed lead (in saved order); newly installed apps follow in their
 * existing alphabetical order; uninstalled saved entries are dropped.
 */
private fun mergeOrder(
    detected: List<LaunchableApp>,
    savedOrder: List<String>,
): List<LaunchableApp> {
    if (savedOrder.isEmpty()) return detected
    val byPackage = detected.associateBy { it.packageName }
    val ordered = savedOrder.mapNotNull { byPackage[it] }
    val savedSet = savedOrder.toHashSet()
    val rest = detected.filter { it.packageName !in savedSet }
    return ordered + rest
}

private fun Key.isCenterKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
