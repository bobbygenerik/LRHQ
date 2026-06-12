package com.livingroomhq

import android.app.Application
import com.livingroomhq.core.data.repo.AmbientInfoRepository
import com.livingroomhq.core.data.repo.ChannelRepository
import com.livingroomhq.core.data.repo.DemoAmbientInfoRepository
import com.livingroomhq.core.data.repo.DemoChannelRepository
import com.livingroomhq.core.data.repo.DemoMediaRepository
import com.livingroomhq.core.data.repo.InstalledAppsRepository
import com.livingroomhq.core.data.repo.MediaRepository
import com.livingroomhq.core.data.repo.SystemMonitor
import com.livingroomhq.core.widget.WidgetRegistry
import com.livingroomhq.widgets.registerBuiltInWidgets

/**
 * Composition root. The launcher must cold-start fast, so wiring is plain
 * lazy properties rather than a DI framework — nothing is constructed until
 * the first screen asks for it.
 */
class HqApplication : Application() {

    val channels: ChannelRepository by lazy { DemoChannelRepository() }
    val media: MediaRepository by lazy { DemoMediaRepository() }
    val ambientInfo: AmbientInfoRepository by lazy { DemoAmbientInfoRepository() }
    val systemMonitor: SystemMonitor by lazy { SystemMonitor(this) }
    val installedApps: InstalledAppsRepository by lazy { InstalledAppsRepository(this) }
    val widgets: WidgetRegistry by lazy {
        WidgetRegistry().also { registerBuiltInWidgets(it, this) }
    }
}
