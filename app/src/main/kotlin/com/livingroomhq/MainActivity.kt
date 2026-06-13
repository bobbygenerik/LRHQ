package com.livingroomhq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.livingroomhq.components.Sidebar
import com.livingroomhq.core.data.repo.LocalMediaRepository
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.navigation.SpatialNavHost
import com.livingroomhq.navigation.Zone
import com.livingroomhq.screens.AmbientScreen
import com.livingroomhq.screens.CommandCenterScreen
import com.livingroomhq.screens.HomeScreen
import com.livingroomhq.screens.LiveScreen
import com.livingroomhq.screens.MediaScreen
import com.livingroomhq.screens.SettingsScreen
import com.livingroomhq.screens.ToolsScreen
import com.livingroomhq.ui.MessageOverlay
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var nav: SpatialNavController
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            ((application as HqApplication).media as? LocalMediaRepository)?.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestMediaPermissions()

        val app = application as HqApplication
        nav = SpatialNavController()

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
                        controller.goTo(Zone.AMBIENT)
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
                        SpatialNavHost(zone = controller.zone, modifier = Modifier.fillMaxSize()) { zone ->
                            when (zone) {
                                Zone.HOME -> HomeScreen(app, controller, onSettingsChanged = { settings = it })
                                Zone.LIVE -> LiveScreen(app, controller)
                                Zone.MEDIA -> MediaScreen(app, controller)
                                Zone.TOOLS -> ToolsScreen(app, controller)
                                Zone.AMBIENT -> AmbientScreen(app, controller)
                                Zone.COMMAND_CENTER -> CommandCenterScreen(app, controller)
                                Zone.SETTINGS -> SettingsScreen(app, controller, settings, onSettingsChanged = { settings = it })
                            }
                        }
                        MessageOverlay()
                    }
                }
            }
        }
    }

    /**
     * Navigation is driven by the persistent sidebar (focus it with D-pad LEFT,
     * press OK to switch zone) plus the Compose focus system within each screen —
     * the activity no longer hijacks directional presses to slide zones, which
     * previously stole focus that should have moved to the sidebar. We only
     * handle the two global shortcuts: MENU opens the Command Center, BACK
     * returns to Home (and is a no-op at Home, since a launcher has nowhere to
     * back out to).
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
     * MAIN/HOME intent here instead of starting a new instance. Reset to the
     * center zone so HOME always returns to Home, per the navigation model.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::nav.isInitialized) nav.goHome()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::nav.isInitialized) nav.touch()
    }

    private fun requestMediaPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            mediaPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}
