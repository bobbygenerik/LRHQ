package com.livingroomhq

import android.app.Application
import com.livingroomhq.backdrop.AmbientBackdrops
import com.livingroomhq.backdrop.UnsplashClient
import com.livingroomhq.core.data.persist.DataStorePrefsStore
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.data.repo.AmbientInfoRepository
import com.livingroomhq.core.data.repo.ChannelRepository
import com.livingroomhq.core.data.repo.InstalledAppsRepository
import com.livingroomhq.core.data.repo.LocalMediaRepository
import com.livingroomhq.core.data.repo.MediaRepository
import com.livingroomhq.core.data.repo.PersistentChannelRepository
import com.livingroomhq.core.data.repo.SystemMonitor
import com.livingroomhq.core.data.repo.UnconfiguredAmbientInfoRepository
import com.livingroomhq.core.widget.WidgetRegistry
import com.livingroomhq.tvintegration.WatchNextPublisher
import com.livingroomhq.tvintegration.toWatchNextEntry
import com.livingroomhq.ui.UiMessages
import com.livingroomhq.widgets.registerBuiltInWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val media: MediaRepository by lazy { LocalMediaRepository(this, appScope) }
    val ambientInfo: AmbientInfoRepository by lazy { UnconfiguredAmbientInfoRepository() }
    val systemMonitor: SystemMonitor by lazy { SystemMonitor(this) }
    val installedApps: InstalledAppsRepository by lazy {
        InstalledAppsRepository(this) { UiMessages.post("Couldn't open that app") }
    }
    val widgets: WidgetRegistry by lazy {
        WidgetRegistry().also { registerBuiltInWidgets(it, this) }
    }
    val watchNext: WatchNextPublisher by lazy { WatchNextPublisher(this) }

    private val _ambientBackdropUrls = MutableStateFlow(AmbientBackdrops.urls)

    /** Landscape stills for the hero / ambient cycle; bundled set until Unsplash responds. */
    val ambientBackdropUrls: StateFlow<List<String>> = _ambientBackdropUrls.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        // Keep the system Watch Next row in step with the library.
        appScope.launch {
            media.library.collect { items ->
                watchNext.sync(items.mapNotNull { it.toWatchNextEntry() })
            }
        }
        // Refresh the ambient backdrop pool once per launch (demo tier: 50 req/hr).
        appScope.launch {
            val urls = UnsplashClient.fetchLandscapeUrls()
            if (urls.isNotEmpty()) _ambientBackdropUrls.value = urls
        }
    }
}
