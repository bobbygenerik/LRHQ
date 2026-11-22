package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program

/** Pure, unit-testable playlist↔guide matching engine. */
object GuideMatcher {

    // Provider IDs are sometimes different even for the same station. These
    // reviewed aliases apply only when the requested destination exists.
    private val stationIds = mapOf(
        "abcwlneprovidenceri.us" to "ABCWJARDT2.us", // guide labels this ABC 6 WLNE
        "nbc10wjarprovidencerhodeisland.us" to "NBCWJAR.us",
        "fox64wnacprovidence.us" to "FOXWNAC.us",
        "cbs12wpriprovidence.us" to "CBSWPRI.us",
        "cbs4wbzboston.us" to "CBSWBZ.us",
        "abcwcvbboston.us" to "ABCWCVB.us",
        "nbc10wbtsboston.us" to "NBCWBTS.us",
        "fox25wfxtboston.us" to "FOXWFXT.us",
        "38wsbkboston.us" to "MYWSBK.us",
        "cw56wlviboston.us" to "CWWLVI.us",
        "pbswgbhboston.us" to "PBSWGBH.us",
        "nbcwbtsboston.us" to "nbc10wbts.us",
    )

    private val callSign = Regex("(?<![A-Za-z])([KW][A-Z]{2,3})(?:[0-9]+)?(?![A-Za-z])")

    /** Whole names only: a shared word is not evidence of channel identity. */
    fun channelAliases(channel: Channel): Set<String> = buildSet {
        listOfNotNull(channel.tvgName, channel.name).forEach { name ->
            addAll(name.fullGuideNameKeys())
        }
    }

    fun guideAliasKeys(id: String, displayNames: Collection<String>): Set<String> = buildSet {
        addAll(id.guideMatchKeys())
        displayNames.forEach { addAll(it.fullGuideNameKeys()) }
        // Namespaced callsigns cannot collide with ordinary words or numbers.
        displayNames.forEach { name ->
            addAll(stationKeys(name))
        }
    }

    /** Ambiguous aliases remain absent, independent of provider channel order. */
    fun buildAliasIndex(guideAliases: Map<String, Set<String>>): Map<String, String> {
        val index = mutableMapOf<String, String>()
        val ambiguous = mutableSetOf<String>()
        guideAliases.forEach { (id, aliases) ->
            val baseKeys = aliases + id.guideMatchKeys()
            val region = country(id)
            val keys = baseKeys + baseKeys.map { "country:$region:$it" } + setOf(
                "id:" + id.substringBefore('@').lowercase(),
                "nid:" + id.substringBefore('@').normalizedGuideKey(),
            )
            keys.forEach { key ->
                if (key !in ambiguous) {
                    val previous = index.putIfAbsent(key, id)
                    if (previous != null && previous != id) {
                        index.remove(key)
                        ambiguous.add(key)
                    }
                }
            }
        }
        return index
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

    /** Exact identity, reviewed station alias, unique full name, then unique callsign. */
    fun resolveGuideChannelId(
        channel: Channel,
        guideAliases: Map<String, Set<String>>,
        aliasIndex: Map<String, String>,
    ): String? {
        fun exact(id: String): String? {
            if (id.isBlank()) return null
            if (guideAliases.containsKey(id)) return id
            return aliasIndex["id:" + id.substringBefore('@').lowercase()]
        }
        val ids = listOfNotNull(channel.tvgId, channel.id.substringBefore('#'))
        ids.forEach { exact(it)?.let { match -> return match } }

        val providerId = channel.tvgId?.lowercase()
        // This provider reuses one ID for three unrelated Boston channels.
        val reviewed = if (providerId == "news7boston.us") {
            when (channel.name.normalizedGuideKey()) {
                "cbsnewsboston" -> "CBSNewsBoston.pluto"
                "boston25newsfox" -> "FOXWFXT.us"
                "7newsbostonwhdh" -> "NewsWHDH.us"
                else -> null
            }
        } else stationIds[providerId]
        reviewed?.let { exact(it)?.let { match -> return match } }

        // Normalize only when the candidate is also the same normalized ID.
        // Never allow an ID lookup to be satisfied by a display-name token.
        ids.forEach { id ->
            val key = id.substringBefore('@').normalizedGuideKey()
            aliasIndex["nid:" + key]?.let { candidate ->
                if (candidate.substringBefore('@').normalizedGuideKey() == key &&
                    compatibleCountry(channel, candidate) &&
                    !hasConflictingDirection(channel.name, candidate)) return candidate
            }
        }

        val names = channelAliases(channel)
        val fullMatches = names.mapNotNull {
            aliasIndex["country:${country(channel.tvgId)}:$it"] ?: aliasIndex[it]
        }.filter {
            compatibleCountry(channel, it) && !hasConflictingDirection(channel.name, it)
        }.distinct()
        if (fullMatches.size == 1) return fullMatches.single()
        if (fullMatches.size > 1) return null

        val stations = listOfNotNull(channel.name, channel.tvgName).flatMap { name ->
            stationKeys(name)
        }.distinct()
        val stationMatches = stations.mapNotNull {
            aliasIndex["country:${country(channel.tvgId)}:$it"] ?: aliasIndex[it]
        }
            .filter { compatibleCountry(channel, it) && !hasConflictingDirection(channel.name, it) }
            .distinct()
        return stationMatches.singleOrNull()
    }

    private fun stationKeys(name: String): List<String> {
        // A callsign alone cannot distinguish digital subchannels.
        if (Regex("(?i)([0-9]+[.-][0-9]+|[- ](?:DT|TV)[- ]?[0-9]+)").containsMatchIn(name)) return emptyList()
        return callSign.findAll(name)
            .filter { it.groupValues[1] !in setOf("WEST", "KIDS", "WITH", "WORK") }
            .map { "station:" + it.groupValues[1].lowercase() }.toList()
    }

    private fun country(id: String?): String? = id?.substringBefore('@')?.substringAfterLast('.', "")
        ?.lowercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) }

    private fun compatibleCountry(channel: Channel, guideId: String): Boolean {
        val source = country(channel.tvgId)
        val target = country(guideId)
        return source == null || target == null || source == target
    }

    private fun hasConflict(channelName: String, guideIdOrName: String): Boolean =
        hasConflictingNumber(channelName, guideIdOrName) ||
            hasConflictingDirection(channelName, guideIdOrName)

    fun hasConflictingNumber(a: String, b: String): Boolean {
        val digitsA = a.filter { it.isDigit() }
        val digitsB = b.filter { it.isDigit() }
        return (digitsA.isNotEmpty() || digitsB.isNotEmpty()) && digitsA != digitsB
    }

    fun hasConflictingDirection(a: String, b: String): Boolean {
        val aLower = a.lowercase()
        val bLower = b.lowercase()
        val aEast = "east" in aLower
        val aWest = "west" in aLower
        val bEast = "east" in bLower
        val bWest = "west" in bLower
        return (aEast && bWest) || (aWest && bEast)
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

internal fun String.normalizedGuideTokens(): Set<String> {
    return split(Regex("[^A-Za-z0-9]+"))
        .map { it.normalizedGuideKey() }
        .filter { it.length >= 3 && !it.all { ch -> ch.isDigit() } }
        .toSet()
}

internal fun String.normalizedGuideKey(): String =
    lowercase()
        .replace(Regex("\\b(hd|fhd|uhd|sd|us|usa)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), "")

/** Remove provider labels and quality markers, retaining every meaningful word. */
private fun String.fullGuideNameKeys(): Set<String> = buildSet {
    add(normalizedGuideKey())
    // XMLTV often prefixes a complete name with "US:", "Xumo:" or state/city pipes.
    val display = substringAfterLast('|').substringAfterLast(':').trim()
    add(display.normalizedGuideKey())
}.filterTo(linkedSetOf()) { it.isNotEmpty() }
