package com.livingroomhq

import android.app.Application
import com.livingroomhq.backdrop.AmbientBackdrops
import com.livingroomhq.backdrop.AmbientPhoto
import com.livingroomhq.backdrop.AmbientPhotoCacheRepository
import com.livingroomhq.backdrop.GooglePhotosPickerClient
import com.livingroomhq.backdrop.UnsplashClient
import com.livingroomhq.player.LivePreviewEngine
import com.livingroomhq.core.data.db.LrhqDatabase
import com.livingroomhq.core.data.persist.DataStorePrefsStore
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.data.repo.AmbientInfoRepository
import com.livingroomhq.core.data.repo.ChannelRepository
import com.livingroomhq.core.data.repo.InstalledAppsRepository
import com.livingroomhq.core.data.repo.LocalMediaRepository
import com.livingroomhq.core.data.repo.MediaRepository
import com.livingroomhq.core.data.repo.PersistentChannelRepository
import com.livingroomhq.core.data.repo.RealAmbientInfoRepository
import com.livingroomhq.core.data.repo.SystemMonitor
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
import kotlinx.coroutines.runBlocking

/**
 * Composition root. The launcher must cold-start fast, so wiring is plain
 * lazy properties rather than a DI framework — nothing is constructed until
 * the first screen asks for it.
 */
class HqApplication : Application() {

    /** App-lifetime scope for repository persistence and background sync. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs: LauncherPrefsStore by lazy { DataStorePrefsStore(this) }
    val database: LrhqDatabase by lazy { LrhqDatabase.build(this) }
    val channels: ChannelRepository by lazy {
        PersistentChannelRepository(database.iptvDao(), prefs, appScope).also { it.restore() }
    }
    val media: MediaRepository by lazy { LocalMediaRepository(this, appScope) }
    val ambientInfo: AmbientInfoRepository by lazy { RealAmbientInfoRepository(appScope) }
    val systemMonitor: SystemMonitor by lazy { SystemMonitor(this) }
    val installedApps: InstalledAppsRepository by lazy {
        InstalledAppsRepository(this) { UiMessages.post("Couldn't open that app") }
    }
    val widgets: WidgetRegistry by lazy {
        WidgetRegistry().also { registerBuiltInWidgets(it, this) }
    }
    val watchNext: WatchNextPublisher by lazy { WatchNextPublisher(this) }
    val ambientPhotoCache: AmbientPhotoCacheRepository by lazy { AmbientPhotoCacheRepository(this) }
    val googlePhotosPicker: GooglePhotosPickerClient by lazy { GooglePhotosPickerClient(ambientPhotoCache) }
    val livePreviewEngine: LivePreviewEngine by lazy { LivePreviewEngine(this) }

    private val _ambientBackdropPhotos = MutableStateFlow(AmbientBackdrops.photos)
    private val _remoteAmbientPhotos = MutableStateFlow(AmbientBackdrops.photos)

    /** Credited landscape stills for the hero / ambient cycle; bundled set until Unsplash responds. */
    val ambientBackdropPhotos: StateFlow<List<AmbientPhoto>> = _ambientBackdropPhotos.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        // Room open + channel restore must not run on the main thread during first
        // composition — a large EPG WAL can take seconds and ANR the launcher.
        runBlocking(Dispatchers.IO) {
            database
            channels
            ambientPhotoCache.restore()
        }
        // Keep the system Watch Next row in step with the library.
        appScope.launch {
            media.library.collect { items ->
                watchNext.sync(items.mapNotNull { it.toWatchNextEntry() })
            }
        }
        appScope.launch {
            ambientPhotoCache.photos.collect { cached ->
                _ambientBackdropPhotos.value = cached.takeIf { it.isNotEmpty() } ?: _remoteAmbientPhotos.value
            }
        }
        // Refresh the ambient backdrop pool once per launch (demo tier: 50 req/hr).
        appScope.launch {
            val photos = UnsplashClient.fetchLandscapePhotos()
            if (photos.isNotEmpty()) {
                _remoteAmbientPhotos.value = photos
                if (ambientPhotoCache.photos.value.isEmpty()) _ambientBackdropPhotos.value = photos
            }
        }
    }
}
