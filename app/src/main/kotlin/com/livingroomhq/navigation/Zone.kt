package com.livingroomhq.navigation

/**
 * The five spatial zones plus the Command Center overlay.
 *
 * ```
 *           LIVE
 *  TOOLS    HOME    MEDIA
 *          AMBIENT
 * ```
 *
 * Offsets are grid coordinates used to slide the world in the matching
 * direction, so moving right genuinely feels like travelling right.
 */
enum class Zone(val gridX: Int, val gridY: Int) {
    HOME(0, 0),
    LIVE(0, -1),
    MEDIA(1, 0),
    TOOLS(-1, 0),
    AMBIENT(0, 1),
    COMMAND_CENTER(0, 0),
}

/** Where a D-pad press at the edge of [from] leads, or null to stay. */
fun zoneInDirection(from: Zone, direction: Direction): Zone? = when (from) {
    Zone.HOME -> when (direction) {
        Direction.UP -> Zone.LIVE
        Direction.DOWN -> Zone.AMBIENT
        Direction.LEFT -> Zone.TOOLS
        Direction.RIGHT -> Zone.MEDIA
    }
    Zone.LIVE -> if (direction == Direction.DOWN) Zone.HOME else null
    Zone.MEDIA -> if (direction == Direction.LEFT) Zone.HOME else null
    Zone.TOOLS -> if (direction == Direction.RIGHT) Zone.HOME else null
    Zone.AMBIENT -> if (direction == Direction.UP) Zone.HOME else null
    Zone.COMMAND_CENTER -> null
}

enum class Direction { UP, DOWN, LEFT, RIGHT }
