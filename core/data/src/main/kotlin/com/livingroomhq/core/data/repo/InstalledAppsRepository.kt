package com.livingroomhq.core.data.repo

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.livingroomhq.core.data.model.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Foreground launcher activity; registered while the host activity is resumed. */
    @Volatile
    private var hostActivity: Activity? = null

    /** True after we start a third-party app until the host resumes again. */
    private var launchedExternalApp = false

    /** Blocks phantom OK/click relaunches when returning from an external app. */
    private var blockLaunchUntil = 0L

    private val _hostResumeTick = MutableStateFlow(0)
    /** Increments on every host [onHostResumed] so UI can drop stale launch arms. */
    val hostResumeTick: StateFlow<Int> = _hostResumeTick.asStateFlow()

    fun setHostActivity(activity: Activity) {
        hostActivity = activity
    }

    fun clearHostActivity() {
        hostActivity = null
    }

    /** Call when the launcher activity resumes so stale focus clicks cannot relaunch. */
    fun onHostResumed(onReturnedFromExternal: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        blockLaunchUntil = maxOf(blockLaunchUntil, now + RESUME_LAUNCH_GUARD_MS)
        _hostResumeTick.value++
        if (launchedExternalApp) {
            blockLaunchUntil = now + RETURN_LAUNCH_GUARD_MS
            launchedExternalApp = false
            onReturnedFromExternal()
        }
    }

    fun canLaunch(): Boolean = System.currentTimeMillis() >= blockLaunchUntil

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

    /**
     * Launches the app, reporting failure (uninstalled race, no intent, blocked).
     *
     * Starts on the host activity's back stack when possible so Back returns to
     * LRHQ. Leanback intents ship with [Intent.FLAG_ACTIVITY_NEW_TASK], which
     * strands apps like TizenTube in a separate task when left in place.
     */
    fun launch(packageName: String, launcher: Context? = null): Boolean {
        if (!canLaunch()) return false

        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            onLaunchError(packageName)
            return false
        }

        val activity = hostActivity ?: (launcher ?: context).findActivity()
        return try {
            if (activity != null) {
                intent.flags = 0
                activity.startActivity(intent)
                launchedExternalApp = true
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            launchedExternalApp = false
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

    private companion object {
        const val RESUME_LAUNCH_GUARD_MS = 2_500L
        const val RETURN_LAUNCH_GUARD_MS = 8_000L
    }
}

/** Walk [ContextWrapper] chains from Compose's themed context to the hosting activity. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        if (current !is ContextWrapper) return null
        current = current.baseContext
    }
}
