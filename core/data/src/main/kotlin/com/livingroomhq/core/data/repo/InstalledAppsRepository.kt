package com.livingroomhq.core.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.livingroomhq.core.data.model.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries the package manager for launchable apps, preferring Leanback
 * launcher entries (TV-native apps) and falling back to standard launcher
 * activities for sideloaded tools.
 */
class InstalledAppsRepository(
    private val context: Context,
    private val onLaunchError: (packageName: String) -> Unit = {},
) {

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

    /** Launches the app, reporting failure (uninstalled race, no intent, blocked). */
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            onLaunchError(packageName)
            return false
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            // ActivityNotFoundException or SecurityException from a stale entry.
            onLaunchError(packageName)
            false
        }
    }

    /** Opens the system "App info" / settings page for the given package. */
    fun openAppSettings(packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Some TV builds restrict the per-app settings deep link; fall back to all-apps settings.
            val fallback = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(fallback)
                true
            } catch (e2: Exception) {
                onLaunchError(packageName)
                false
            }
        }
    }
}
