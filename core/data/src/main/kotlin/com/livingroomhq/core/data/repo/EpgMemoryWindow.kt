package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Program

/** In-memory EPG horizon — absolute UTC millis, safe for worldwide channels. */
const val EPG_MEMORY_WINDOW_HOURS = 12

fun epgMemoryWindowEnd(nowMillis: Long): Long =
    nowMillis + EPG_MEMORY_WINDOW_HOURS * 60L * 60L * 1000L

/**
 * Keeps programmes that are still relevant for now/next lookup:
 * - anything still on air ([endMillis] > now), including long slots that started days ago
 * - anything scheduled to start within the next [EPG_MEMORY_WINDOW_HOURS]
 *
 * Far-future or expired rows stay in Room; callers fall back to DB when needed.
 */
fun filterProgramsForMemoryCache(
    programs: Iterable<Program>,
    nowMillis: Long,
    windowEndMillis: Long = epgMemoryWindowEnd(nowMillis),
): List<Program> =
    programs.filter { program ->
        program.endMillis > nowMillis && program.startMillis < windowEndMillis
    }

fun groupProgramsByChannel(programs: Iterable<Program>): Map<String, List<Program>> =
    programs
        .groupBy { it.channelId }
        .mapValues { (_, list) -> list.sortedBy { it.startMillis } }
