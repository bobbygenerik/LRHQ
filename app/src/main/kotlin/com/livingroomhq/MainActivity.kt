package com.livingroomhq

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.livingroomhq.components.Sidebar
import com.livingroomhq.core.ui.theme.CustomSettings
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var nav: LauncherNavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyLauncherWindowPolicy()

        val app = application as HqApplication
        nav = LauncherNavController()

        setContent {
            val controller = remember { nav }
            var settings by remember { mutableStateOf(CustomSettings()) }

            // Dynamic accent color updating
            LaunchedEffect(settings.accentColor) {
                HqColors.Accent = if (settings.accentColor == "Blue") Color(0xFF6FB6FF) else Color(0xFF2BE080)
            }

            // Idle ticker that drops the launcher into Ambient Mode.
            LaunchedEffect(settings.idleTimeSeconds) {
                val timeoutMillis = settings.idleTimeSeconds * 1000L
                while (true) {
                    delay(5_000)
                    if (controller.zone != Zone.AMBIENT && System.currentTimeMillis() - controller.lastInteractionAt >= timeoutMillis) {
                        controller.enterAmbientFromIdle()
                    }
                }
            }

            CompositionLocalProvider(LocalCustomSettings provides settings) {
                Row(Modifier.fillMaxSize()) {
                    if (controller.zone != Zone.AMBIENT) {
                        Sidebar(
                            currentZone = controller.zone,
                            onZoneSelected = { zone -> controller.goTo(zone) },
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        LauncherNavHost(zone = controller.zone, modifier = Modifier.fillMaxSize()) { zone ->
                            when (zone) {
                                Zone.HOME -> HomeScreen(app, controller)
                                Zone.LIVE -> LiveScreen(app)
                                Zone.TOOLS -> ToolsScreen(app)
                                Zone.AMBIENT -> AmbientScreen(app)
                                Zone.COMMAND_CENTER -> CommandCenterScreen(app)
                                Zone.SETTINGS -> SettingsScreen(app, settings, onSettingsChanged = { settings = it })
                            }
                        }
                        MessageOverlay()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyLauncherWindowPolicy()
    }

    override fun onPause() {
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
     * Navigation is driven by the persistent sidebar (focus it with D-pad LEFT,
     * press OK to switch tabs) plus the Compose focus system within each screen.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        nav.touch()
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                nav.goTo(Zone.COMMAND_CENTER)
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (nav.zone != Zone.HOME) nav.goHome()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /**
     * As the default home app the activity is `singleTask`, so pressing the
     * hardware HOME button while another app is foregrounded re-delivers the
     * MAIN/HOME intent here instead of starting a new instance. Reset to Home.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::nav.isInitialized) nav.goHome()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::nav.isInitialized) nav.touch()
    }
}
