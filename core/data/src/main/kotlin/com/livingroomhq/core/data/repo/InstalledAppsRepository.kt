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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Queries the package manager for launchable apps, preferring Leanback
 * launcher entries (TV-native apps) and falling back to standard launcher
 * activities for sideloaded tools.
 */
class InstalledAppsRepository(
    private val context: Context,
    private val pinnedPackages: suspend () -> List<String> = { emptyList() },
    private val onLaunchError: (packageName: String) -> Unit = {},
) {

    /** Foreground launcher activity; registered while the host activity is resumed. */
    @Volatile
    private var hostActivity: Activity? = null

    /** True after we start a third-party app until the host resumes again. */
    var launchedExternalApp = false
        private set

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

    /** HOME was pressed while a third-party app was foregrounded. */
    fun onHomePressed() {
        launchedExternalApp = false
        blockLaunchUntil = System.currentTimeMillis() + RETURN_LAUNCH_GUARD_MS
        _hostResumeTick.value++
    }

    fun canLaunch(): Boolean = System.currentTimeMillis() >= blockLaunchUntil

    suspend fun launchableApps(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvApps = pm.queryIntentActivities(tvIntent, 0).map { it.activityInfo.packageName }.toSet()

        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = pm.queryIntentActivities(mainIntent, 0) +
            pm.queryIntentActivities(tvIntent, 0)

        val detected = all.distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    isTvApp = it.activityInfo.packageName in tvApps,
                )
            }

        val detectedPackages = detected.map { it.packageName }.toSet()
        val extraPackages = pinnedPackages().filter { it !in detectedPackages }
        val extraApps = extraPackages.mapNotNull { pkg ->
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
                LaunchableApp(
                    packageName = pkg,
                    label = label,
                    isTvApp = true,
                )
            } catch (e: Exception) {
                null
            }
        }

        (detected + extraApps).sortedBy { it.label.lowercase() }
    }

    /**
     * Launches the app, reporting failure (uninstalled race, no intent, blocked).
     *
     * Third-party apps always run in their own task ([Intent.FLAG_ACTIVITY_NEW_TASK])
     * so the default-home launcher can be brought back with the hardware HOME button
     * even after an install/replace while the external app is foregrounded.
     */
    fun launch(packageName: String, launcher: Context? = null): Boolean {
        if (!canLaunch()) return false

        var intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                setPackage(packageName)
            }
            val resolveInfos = context.packageManager.queryIntentActivities(mainIntent, 0)
            val info = resolveInfos.firstOrNull()
            if (info != null) {
                intent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(info.activityInfo.packageName, info.activityInfo.name)
                }
            }
        }
        if (intent == null) {
            onLaunchError(packageName)
            return false
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val targetContext = launcher ?: hostActivity ?: context

        return try {
            targetContext.startActivity(intent)
            launchedExternalApp = true
            true
        } catch (e: Exception) {
            launchedExternalApp = false
            onLaunchError(packageName)
            false
        }
    }

    /**
     * Opens the system "App info" / settings page for the given package.
     *
     * Always uses a new task: Shield's AppManagementActivity has its own
     * taskAffinity and misbehaves when forced into the home task (settings
     * bails and the OK click falls through into the app under the menu).
     *
     * Arms a short [suppressHomeResetUntil] window so a spurious MAIN/HOME
     * redelivery when settings closes does not yank the Apps tab back to Home.
     */
    fun openAppSettings(packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = hostActivity ?: context
        val now = System.currentTimeMillis()
        return try {
            target.startActivity(intent)
            suppressHomeResetUntil = now + SETTINGS_HOME_SUPPRESS_MS
            // Absorb the OK KeyUp that dismissed the action menu so it cannot
            // arm/launch the app card under the overlay.
            blockLaunchUntil = now + RETURN_LAUNCH_GUARD_MS
            true
        } catch (e: Exception) {
            // Some TV builds restrict the per-app settings deep link; fall back to all-apps settings.
            val fallback = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                target.startActivity(fallback)
                suppressHomeResetUntil = now + SETTINGS_HOME_SUPPRESS_MS
                blockLaunchUntil = now + RETURN_LAUNCH_GUARD_MS
                true
            } catch (e2: Exception) {
                onLaunchError(packageName)
                false
            }
        }
    }

    /** Until this uptime, MAIN/HOME [onNewIntent] should keep the current tab. */
    @Volatile
    private var suppressHomeResetUntil = 0L

    /** Returns true once if a settings-return should skip the Home-tab reset. */
    fun consumeSuppressHomeReset(): Boolean {
        val until = suppressHomeResetUntil
        if (until == 0L || System.currentTimeMillis() > until) {
            suppressHomeResetUntil = 0L
            return false
        }
        suppressHomeResetUntil = 0L
        return true
    }

    private companion object {
        const val RESUME_LAUNCH_GUARD_MS = 2_500L
        const val RETURN_LAUNCH_GUARD_MS = 1_500L
        const val SETTINGS_HOME_SUPPRESS_MS = 8_000L
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
