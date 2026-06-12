package com.livingroomhq

import android.app.Application
import com.livingroomhq.core.data.persist.DataStorePrefsStore
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.data.repo.AmbientInfoRepository
import com.livingroomhq.core.data.repo.ChannelRepository
import com.livingroomhq.core.data.repo.DemoAmbientInfoRepository
import com.livingroomhq.core.data.repo.DemoMediaRepository
import com.livingroomhq.core.data.repo.InstalledAppsRepository
import com.livingroomhq.core.data.repo.MediaRepository
import com.livingroomhq.core.data.repo.PersistentChannelRepository
import com.livingroomhq.core.data.repo.SystemMonitor
import com.livingroomhq.core.widget.WidgetRegistry
import com.livingroomhq.ui.UiMessages
import com.livingroomhq.widgets.registerBuiltInWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composition root. The launcher must cold-start fast, so wiring is plain
 * lazy properties rather than a DI framework — nothing is constructed until
 * the first screen asks for it.
 */
class HqApplication : Application() {

    /** App-lifetime scope for repository persistence and background sync. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs: LauncherPrefsStore by lazy { DataStorePrefsStore(this) }
    val channels: ChannelRepository by lazy {
        PersistentChannelRepository(prefs, appScope).also { it.restore() }
    }
    val media: MediaRepository by lazy { DemoMediaRepository() }
    val ambientInfo: AmbientInfoRepository by lazy { DemoAmbientInfoRepository() }
    val systemMonitor: SystemMonitor by lazy { SystemMonitor(this) }
    val installedApps: InstalledAppsRepository by lazy {
        InstalledAppsRepository(this) { UiMessages.post("Couldn't open that app") }
    }
    val widgets: WidgetRegistry by lazy {
        WidgetRegistry().also { registerBuiltInWidgets(it, this) }
    }
}
