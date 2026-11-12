package com.livingroomhq.screens.live

import com.livingroomhq.core.data.model.Program

const val GUIDE_HALF_HOUR = 30 * 60_000L
const val GUIDE_VIEW_DURATION = 3 * GUIDE_HALF_HOUR
const val GUIDE_HORIZON = 12 * 60 * 60_000L

data class GuideSlot(val startMillis: Long, val endMillis: Long, val program: Program?)

fun guideStart(nowMillis: Long): Long = Math.floorDiv(nowMillis, GUIDE_HALF_HOUR) * GUIDE_HALF_HOUR

/** A continuous timeline. Missing listings remain honest gaps, and malformed overlaps cannot stack cells. */
fun guideSlots(programs: List<Program>, startMillis: Long, endMillis: Long): List<GuideSlot> {
    if (endMillis <= startMillis) return emptyList()
    val result = mutableListOf<GuideSlot>()
    var cursor = startMillis
    for (program in programs.sortedBy { it.startMillis }) {
        if (program.endMillis <= program.startMillis || program.endMillis <= cursor) continue
        if (program.startMillis >= endMillis) break
        val start = maxOf(cursor, program.startMillis)
        if (start > cursor) result += GuideSlot(cursor, start, null)
        val end = minOf(program.endMillis, endMillis)
        result += GuideSlot(start, end, program)
        cursor = end
        if (cursor == endMillis) break
    }
    if (cursor < endMillis) result += GuideSlot(cursor, endMillis, null)
    return result
}

fun guideSlotAt(slots: List<GuideSlot>, timeMillis: Long): GuideSlot? =
    slots.firstOrNull { timeMillis >= it.startMillis && timeMillis < it.endMillis }

/** Preserve the same broadcast time when changing channels; horizontal movement visits adjacent shows. */
fun moveGuideTime(slots: List<GuideSlot>, timeMillis: Long, direction: Int): Long {
    val current = guideSlotAt(slots, timeMillis) ?: return timeMillis
    val next = if (direction > 0) {
        if (current.program == null) minOf(timeMillis + GUIDE_HALF_HOUR, current.endMillis)
        else current.endMillis
    } else {
        if (current.program == null) maxOf(timeMillis - GUIDE_HALF_HOUR, current.startMillis - 1)
        else current.startMillis - 1
    }
    return next.coerceIn(slots.first().startMillis, slots.last().endMillis - 1)
}

fun guideWindowFor(timeMillis: Long, windowStart: Long, earliest: Long): Long {
    val proposed = when {
        timeMillis < windowStart -> guideStart(timeMillis)
        // Keep a newly selected show away from the trailing sliver of the viewport.
        timeMillis >= windowStart + GUIDE_VIEW_DURATION - GUIDE_HALF_HOUR -> guideStart(timeMillis) - GUIDE_HALF_HOUR
        else -> windowStart
    }
    return proposed.coerceIn(earliest, earliest + GUIDE_HORIZON - GUIDE_VIEW_DURATION)
}
