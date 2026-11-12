package com.livingroomhq.navigation

/**
 * Top-level launcher destinations selected from the sidebar or programmatically.
 *
 * [order] encodes the sidebar's visual top-to-bottom sequence so the nav host
 * can derive slide direction without maintaining a parallel `when`. AMBIENT
 * is the screensaver zone — entered programmatically, not from the sidebar —
 * so its order sits after SETTINGS and the nav host cross-fades instead of
 * sliding to or from it.
 */
enum class Zone(val order: Int) {
    HOME(0),
    LIVE(1),
    VOD(2),
    TOOLS(3),
    COMMAND_CENTER(4),
    SETTINGS(5),
    AMBIENT(6),
}
