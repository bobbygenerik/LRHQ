package com.livingroomhq.navigation

/**
 * Pure policy deciding whether a received intent should force navigation back to Home zone.
 */
object HomeIntentPolicy {

    /**
     * Determines whether [nav.goHome()] should be called.
     *
     * @param hasHomeCategory True if intent contains [android.content.Intent.CATEGORY_HOME]
     * @param returningFromExternal True if an external app was previously launched and is now returning
     * @param suppressHomeReset True if home reset is currently suppressed (e.g. returning from app settings)
     * @param wasAlreadyResumed True if launcher activity was already foregrounded/resumed when intent arrived
     */
    fun shouldGoHome(
        hasHomeCategory: Boolean,
        returningFromExternal: Boolean,
        suppressHomeReset: Boolean,
        wasAlreadyResumed: Boolean,
    ): Boolean {
        if (!hasHomeCategory) return false
        if (suppressHomeReset) return false
        if (returningFromExternal) return true
        // Spurious or duplicate HOME intent redelivered while LRHQ is already active in foreground
        if (wasAlreadyResumed) return false
        // Launcher was not resumed (backgrounded or cold returning)
        return true
    }
}
