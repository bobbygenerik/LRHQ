package com.livingroomhq

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.livingroomhq.navigation.Direction
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.navigation.SpatialNavHost
import com.livingroomhq.navigation.Zone
import com.livingroomhq.screens.AmbientScreen
import com.livingroomhq.screens.CommandCenterScreen
import com.livingroomhq.screens.HomeScreen
import com.livingroomhq.screens.LiveScreen
import com.livingroomhq.screens.MediaScreen
import com.livingroomhq.screens.ToolsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var nav: SpatialNavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as HqApplication
        nav = SpatialNavController()

        setContent {
            val controller = remember { nav }

            // Idle ticker that drops the launcher into Ambient Mode.
            LaunchedEffect(Unit) {
                while (true) {
                    delay(5_000)
                    controller.onIdleTick(System.currentTimeMillis())
                }
            }

            SpatialNavHost(zone = controller.zone, modifier = Modifier.fillMaxSize()) { zone ->
                when (zone) {
                    Zone.HOME -> HomeScreen(app, controller)
                    Zone.LIVE -> LiveScreen(app, controller)
                    Zone.MEDIA -> MediaScreen(app, controller)
                    Zone.TOOLS -> ToolsScreen(app, controller)
                    Zone.AMBIENT -> AmbientScreen(app, controller)
                    Zone.COMMAND_CENTER -> CommandCenterScreen(app, controller)
                }
            }
        }
    }

    /**
     * Zone navigation lives at the activity level: screens consume D-pad
     * events for their own focus first; whatever reaches here is an edge
     * press and slides the world to the neighbouring zone. MENU opens the
     * Command Center; BACK and HOME return to center.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        nav.touch()
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
            KeyEvent.KEYCODE_MENU -> {
                nav.goTo(Zone.COMMAND_CENTER)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (nav.zone != Zone.HOME) {
                    nav.goHome()
                    return true
                }
                return true // Launcher: BACK at home is a no-op.
            }
            else -> null
        }
        if (direction != null && nav.navigate(direction)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::nav.isInitialized) nav.touch()
    }
}
