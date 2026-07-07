package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program

/** Pure, unit-testable playlist↔guide matching engine. */
object GuideMatcher {

    fun channelAliases(channel: Channel): Set<String> =
        buildSet {
            addAll(channel.id.normalizedGuideKeys())
            addAll(channel.id.guideMatchKeys())
            addAll(channel.name.normalizedGuideKeys())
            addAll(channel.name.substringBefore('(').normalizedGuideKeys())
            addAll(channel.name.substringBefore('-').normalizedGuideKeys())
            channel.tvgId?.let {
                addAll(it.normalizedGuideKeys())
                addAll(it.guideMatchKeys())
            }
            channel.tvgName?.let { addAll(it.normalizedGuideKeys()) }
            channel.tvgChno?.let { chno ->
                add(chno)
                add(chno.normalizedGuideKey())
            }
        }.filterTo(mutableSetOf()) { it.isNotEmpty() }

    fun guideAliasKeys(id: String, displayNames: Collection<String>): Set<String> =
        buildSet {
            id.guideMatchKeys().forEach { add(it) }
            displayNames.forEach { name -> addAll(name.normalizedGuideKeys()) }
        }

    fun buildAliasIndex(guideAliases: Map<String, Set<String>>): Map<String, String> =
        buildMap {
            guideAliases.forEach { (guideChannelId, aliases) ->
                aliases.forEach { alias -> putIfAbsent(alias, guideChannelId) }
            }
        }

    fun lookupGuidePrograms(guide: Map<String, List<Program>>, key: String): List<Program>? {
        if (key.isBlank()) return null
        guide[key]?.let { return it }
        val stripped = key.substringBefore('@')
        if (stripped != key) {
            guide[stripped]?.let { return it }
        }
        return guide.entries.firstOrNull { (guideId, _) ->
            guideId.equals(key, ignoreCase = true) ||
                guideId.substringBefore('@').equals(key, ignoreCase = true) ||
                (stripped.isNotEmpty() && guideId.equals(stripped, ignoreCase = true))
        }?.value
    }

    /**
     * Resolves a playlist channel to a guide channel id using tiered matching:
     * exact tvg-id index → fuzzy alias scan.
     */
    fun resolveGuideChannelId(
        channel: Channel,
        guideAliases: Map<String, Set<String>>,
        aliasIndex: Map<String, String>,
    ): String? {
        val aliases = channelAliases(channel)
        aliases.firstNotNullOfOrNull { alias -> aliasIndex[alias] }?.let { return it }
        return guideAliases.entries.firstOrNull { (guideChannelId, guideAliasSet) ->
            guideChannelId.guideMatchKeys().any { it.matchesAnyAlias(aliases) } ||
                guideAliasSet.any { it.matchesAnyAlias(aliases) }
        }?.key
    }

    fun computeNowNext(programs: List<Program>, nowMillis: Long): Pair<Program?, Program?> {
        if (programs.isEmpty()) return null to null
        val current = programs.firstOrNull { nowMillis in it.startMillis until it.endMillis }
        val next = programs.firstOrNull { it.startMillis >= (current?.endMillis ?: nowMillis) }
        return current to next
    }

    fun isNowNextStale(pair: Pair<Program?, Program?>, nowMillis: Long): Boolean {
        val current = pair.first
        if (current != null && nowMillis !in current.startMillis until current.endMillis) return true
        if (current == null && pair.second != null && nowMillis >= pair.second!!.startMillis) return true
        return false
    }

    fun nextInvalidationMillis(
        cache: Map<String, Pair<Program?, Program?>>,
        nowMillis: Long,
    ): Long? {
        var nextAt: Long? = null
        for ((_, pair) in cache) {
            pair.first?.endMillis?.takeIf { it > nowMillis }?.let { end ->
                nextAt = if (nextAt == null) end else minOf(nextAt!!, end)
            }
            if (pair.first == null) {
                pair.second?.startMillis?.takeIf { it > nowMillis }?.let { start ->
                    nextAt = if (nextAt == null) start else minOf(nextAt!!, start)
                }
            }
        }
        return nextAt
    }
}

internal fun String.guideMatchKeys(): Set<String> {
    val stripped = substringBefore('@')
    return buildSet {
        add(this@guideMatchKeys)
        add(stripped)
        add(normalizedGuideKey())
        if (stripped != this@guideMatchKeys) add(stripped.normalizedGuideKey())
    }.filterTo(mutableSetOf()) { it.isNotEmpty() }
}

internal fun String.normalizedGuideKeys(): Set<String> {
    val full = normalizedGuideKey()
    val tokens = split(Regex("[^A-Za-z0-9]+"))
        .map { it.normalizedGuideKey() }
        .filter { it.length >= 3 }
    return (listOf(full) + tokens).filterTo(mutableSetOf()) { it.isNotEmpty() }
}

internal fun String.normalizedGuideKey(): String =
    lowercase()
        .replace(Regex("\\b(hd|fhd|uhd|sd|us|usa)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), "")

internal fun String.matchesAnyAlias(aliases: Set<String>): Boolean =
    isNotEmpty() && aliases.any { alias ->
        (length >= 4 && alias.length >= 4 && this == alias) ||
            (length >= 4 && alias.contains(this)) ||
            (alias.length >= 4 && contains(alias))
    }
