package com.livingroomhq.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.livingroomhq.core.data.repo.PersistentChannelRepository
import java.util.concurrent.TimeUnit

/** Background IPTV sync — survives process death unlike in-process while-loops. */
class IptvSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repositoryProvider: () -> PersistentChannelRepository,
) : CoroutineWorker(appContext, params) {

    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext,
        params,
        repositoryProvider = {
            val app = appContext.applicationContext
            val hqApp = app as? IptvSyncRepositoryProvider
                ?: error("Application must implement IptvSyncRepositoryProvider")
            hqApp.iptvRepositoryForSync()
        },
    )

    override suspend fun doWork(): Result {
        return try {
            val repo = repositoryProvider()
            when (inputData.getString(KEY_TASK)) {
                TASK_EPG_REFRESH -> repo.refreshMemoryEpgFromDatabase()
                TASK_MAINTENANCE -> repo.runMaintenance()
                else -> return Result.failure()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_TASK = "task"
        const val TASK_EPG_REFRESH = "epg_refresh"
        const val TASK_MAINTENANCE = "maintenance"

        private const val EPG_REFRESH_WORK = "iptv_epg_refresh"
        private const val MAINTENANCE_WORK = "iptv_maintenance"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                EPG_REFRESH_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IptvSyncWorker>(15, TimeUnit.MINUTES)
                    .setInputData(androidx.work.workDataOf(KEY_TASK to TASK_EPG_REFRESH))
                    .build(),
            )
            wm.enqueueUniquePeriodicWork(
                MAINTENANCE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IptvSyncWorker>(12, TimeUnit.HOURS)
                    .setInputData(androidx.work.workDataOf(KEY_TASK to TASK_MAINTENANCE))
                    .build(),
            )
        }
    }
}

/** Implemented by [com.livingroomhq.HqApplication] so WorkManager can reach the repository. */
interface IptvSyncRepositoryProvider {
    fun iptvRepositoryForSync(): PersistentChannelRepository
}
