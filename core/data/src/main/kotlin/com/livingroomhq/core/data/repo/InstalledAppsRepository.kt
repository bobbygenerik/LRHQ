package com.livingroomhq.core.data.repo

import android.content.Context
import android.content.Intent
import com.livingroomhq.core.data.model.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries the package manager for launchable apps, preferring Leanback
 * launcher entries (TV-native apps) and falling back to standard launcher
 * activities for sideloaded tools.
 */
class InstalledAppsRepository(private val context: Context) {

    suspend fun launchableApps(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvApps = pm.queryIntentActivities(tvIntent, 0).map { it.activityInfo.packageName }.toSet()

        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = pm.queryIntentActivities(mainIntent, 0) +
            pm.queryIntentActivities(tvIntent, 0)

        all.distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    isTvApp = it.activityInfo.packageName in tvApps,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(packageName: String) {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
