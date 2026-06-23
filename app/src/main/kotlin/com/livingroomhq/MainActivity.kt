package com.livingroomhq

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.livingroomhq.components.LauncherBrandMark
import com.livingroomhq.components.Sidebar
import com.livingroomhq.components.SidebarCollapsedWidth
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.LauncherNavHost
import com.livingroomhq.navigation.Zone
import com.livingroomhq.screens.AmbientScreen
import com.livingroomhq.screens.CommandCenterScreen
import com.livingroomhq.screens.HomeScreen
import com.livingroomhq.screens.LiveScreen
import com.livingroomhq.screens.SettingsScreen
import com.livingroomhq.screens.ToolsScreen
import com.livingroomhq.ui.MessageOverlay
import com.livingroomhq.ui.UiMessages
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var nav: LauncherNavController
    private var homeBackTapCount = 0
    private var homeBackWindowStart = 0L
    private var isResumedState = false

    companion object {
        /** TV remotes are slower than phones; keep the double-Back window generous. */
        private const val DOUBLE_BACK_TO_AMBIENT_MS = 3_000L
        private const val AMBIENT_ENTER_MS = 1_200
        private const val AMBIENT_EXIT_MS = 700
        private const val SIDEBAR_FADE_MS = 900
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when (nav.zone) {
                Zone.AMBIENT -> {
                    resetHomeBackGesture()
                    nav.exitAmbient()
                }
                Zone.HOME -> handleHomeBack(SystemClock.uptimeMillis())
                else -> {
                    resetHomeBackGesture()
                    nav.goHome()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyLauncherWindowPolicy()

        val app = application as HqApplication
        nav = LauncherNavController()
        onBackPressedDispatcher.addCallback(this, backCallback)

        setContent {
            val controller = remember { nav }
            val coroutineScope = rememberCoroutineScope()
            var sidebarExpanded by remember { mutableStateOf(false) }

            val themeState by app.prefs.theme.collectAsState(initial = "Dark")
            val accentColorState by app.prefs.accentColor.collectAsState(initial = "Green")
            val showLivePreviewState by app.prefs.showLivePreview.collectAsState(initial = true)
            val showWeatherState by app.prefs.showWeather.collectAsState(initial = true)
            val idleTimeSecondsState by app.prefs.idleTimeSeconds.collectAsState(initial = 300)
            val animationsState by app.prefs.animations.collectAsState(initial = "Smooth")
            val soundEffectsState by app.prefs.soundEffects.collectAsState(initial = true)

            val settings = remember(
                themeState,
                accentColorState,
                showLivePreviewState,
                showWeatherState,
                idleTimeSecondsState,
                animationsState,
                soundEffectsState
            ) {
                CustomSettings(
                    theme = themeState,
                    accentColor = accentColorState,
                    showLivePreview = showLivePreviewState,
                    showWeather = showWeatherState,
                    idleTimeSeconds = idleTimeSecondsState,
                    animations = animationsState,
                    soundEffects = soundEffectsState
                )
            }

            // Dynamic accent color updating
            LaunchedEffect(settings.accentColor) {
                HqColors.Accent = if (settings.accentColor == "Blue") Color(0xFF6FB6FF) else Color(0xFF2BE080)
            }

            // Idle ticker that drops the launcher into Ambient Mode.
            LaunchedEffect(settings.idleTimeSeconds) {
                val timeoutMillis = settings.idleTimeSeconds * 1000L
                while (true) {
                    delay(5_000)
                    if (isResumedState && controller.zone != Zone.AMBIENT && SystemClock.elapsedRealtime() - controller.lastInteractionAt >= timeoutMillis) {
                        controller.enterAmbientFromIdle()
                    }
                }
            }

            CompositionLocalProvider(LocalCustomSettings provides settings) {
                Box(Modifier.fillMaxSize()) {
                    // Content is inset by the collapsed rail width; the rail floats
                    // on top and expands over content on focus, so focusing the
                    // sidebar never reflows or shoves the whole screen sideways.
                    Box(
                        Modifier
                            .fillMaxSize(),
                    ) {
                        LauncherNavHost(
                            zone = controller.underlyingZone,
                            modifier = Modifier.fillMaxSize(),
                        ) { zone ->
                            when (zone) {
                                Zone.HOME -> HomeScreen(app, controller)
                                Zone.LIVE -> LiveScreen(app, controller)
                                Zone.TOOLS -> ToolsScreen(app, controller)
                                Zone.COMMAND_CENTER -> CommandCenterScreen(app)
                                Zone.SETTINGS -> SettingsScreen(
                                    app = app,
                                    settings = settings,
                                    onSettingsChanged = { newSettings ->
                                        coroutineScope.launch {
                                            if (newSettings.theme != settings.theme) app.prefs.setTheme(newSettings.theme)
                                            if (newSettings.accentColor != settings.accentColor) app.prefs.setAccentColor(newSettings.accentColor)
                                            if (newSettings.showLivePreview != settings.showLivePreview) app.prefs.setShowLivePreview(newSettings.showLivePreview)
                                            if (newSettings.showWeather != settings.showWeather) app.prefs.setShowWeather(newSettings.showWeather)
                                            if (newSettings.idleTimeSeconds != settings.idleTimeSeconds) app.prefs.setIdleTimeSeconds(newSettings.idleTimeSeconds)
                                            if (newSettings.animations != settings.animations) app.prefs.setAnimations(newSettings.animations)
                                            if (newSettings.soundEffects != settings.soundEffects) app.prefs.setSoundEffects(newSettings.soundEffects)
                                        }
                                    }
                                )
                                Zone.AMBIENT -> Unit // drawn as a full-screen overlay below
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = controller.zone != Zone.AMBIENT,
                        enter = fadeIn(tween(SIDEBAR_FADE_MS, easing = LinearOutSlowInEasing)),
                        exit = fadeOut(tween(SIDEBAR_FADE_MS, easing = FastOutLinearInEasing)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Sidebar(
                            currentZone = controller.underlyingZone,
                            onZoneSelected = { zone -> controller.goTo(zone) },
                            onExpandedChanged = { sidebarExpanded = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!sidebarExpanded && controller.zone != Zone.AMBIENT) {
                        LauncherBrandMark(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp, top = HqDimens.SafeVertical),
                        )
                    }
                    AnimatedVisibility(
                        visible = controller.zone == Zone.AMBIENT,
                        enter = fadeIn(tween(AMBIENT_ENTER_MS, easing = LinearOutSlowInEasing)),
                        exit = fadeOut(tween(AMBIENT_EXIT_MS, easing = FastOutLinearInEasing)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AmbientScreen(app)
                    }
                    MessageOverlay()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::nav.isInitialized) {
            nav.resetIdleTimer()
        }
        resetHomeBackGesture()
        isResumedState = true
        val app = application as HqApplication
        app.installedApps.setHostActivity(this)
        app.installedApps.onHostResumed()
        app.fullscreenFocusReturn.onLauncherResumed()
        applyLauncherWindowPolicy()
    }

    override fun onPause() {
        isResumedState = false
        (application as HqApplication).installedApps.clearHostActivity()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    /** Suppress Google TV Ambient Mode (Backdrop) while LRHQ is foreground. */
    private fun applyLauncherWindowPolicy() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawableResource(android.R.color.black)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = AndroidColor.BLACK
            window.navigationBarColor = AndroidColor.BLACK
        }
    }

    /**
     * MENU opens Command Center. Back is handled by [backCallback] so Compose
     * focus and the system back dispatcher stay in sync on TV.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            nav.touch()
            resetHomeBackGesture()
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                nav.touch()
                nav.goTo(Zone.COMMAND_CENTER)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun handleHomeBack(eventTime: Long): Boolean {
        if (homeBackTapCount == 0 || eventTime - homeBackWindowStart > DOUBLE_BACK_TO_AMBIENT_MS) {
            homeBackTapCount = 1
            homeBackWindowStart = eventTime
            UiMessages.post("Press Back again for ambient")
        } else {
            resetHomeBackGesture()
            UiMessages.clear()
            nav.enterAmbientFromIdle()
        }
        return true
    }

    private fun resetHomeBackGesture() {
        homeBackTapCount = 0
        homeBackWindowStart = 0L
    }

    /**
     * As the default home app the activity is `singleTask`, so pressing the
     * hardware HOME button while another app is foregrounded re-delivers the
     * MAIN/HOME intent here instead of starting a new instance. Reset to Home.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val app = application as HqApplication
        if (app.installedApps.launchedExternalApp) return
        if (::nav.isInitialized && isResumedState) nav.goHome()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::nav.isInitialized) nav.touch()
    }
}
