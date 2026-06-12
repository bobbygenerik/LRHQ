package com.livingroomhq.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Show the banner only while we are not the default home and the user hasn't dismissed it. */
fun shouldPromptForDefault(isDefault: Boolean, dismissed: Boolean): Boolean =
    !isDefault && !dismissed

object DefaultHomeHelper {

    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Intent that lets the user make us the default home: the RoleManager
     * system dialog on API 29+, the home-settings screen otherwise. Null when
     * the role is unavailable or already held.
     */
    fun createRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
            if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
