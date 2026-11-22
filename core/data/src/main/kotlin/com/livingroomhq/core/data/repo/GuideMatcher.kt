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
        "globalhalifax.ca" to "GlobalMaritimes-CIHF.ca",
        "global.maritimes.ca2" to "GlobalMaritimes-CIHF.ca",
        "cbcfredericton.ca" to "CBCNewBrunswick-CBAT.ca",
        "cbccharlottetown.ca" to "CBCPrinceEdwardIsland-CBCT.ca",
        "cbcregina.ca" to "CBCSaskatchewan-CBKT.ca",
    )

    private val callSign = Regex("(?<![A-Za-z])([KW][A-Z]{2,3}|C[A-Z]{3})(?:[0-9]+)?(?![A-Za-z])")
    private val subchannelRegex = Regex("(?i)([0-9]+[.-][0-9]+|[- ](?:DT|TV)[- ]?[0-9]+)")
    internal val hyphenCallsignRegex = Regex("^([A-Za-z0-9]+)-[A-Za-z0-9]+(\\.[A-Za-z0-9]+)$")
    internal val leadingLcnRegex = Regex("^[0-9]{1,4}\\s+")
    internal val regionalCitySuffixRegex = Regex(
        "\\s+(london|paris|england|scotland|wales|toronto|montreal|berlin|madrid|rome|roma|sydney|dublin)$",
        RegexOption.IGNORE_CASE,
    )
    internal val prefixStripRegex = Regex(
        "^(?:[\\(\\[]?(?:argentina|azerbaijan|albania|brazil|brasil|russia|turkey|romania|france|united kingdom|great britain|carib|prime|arg|alb|bra|rus|tur|ind|rou|cze|hun|grc|prt|dnk|swe|fin|nor|chl|col|per|usa|deu|ita|esp|mex|pol|irl|aus|nld|bel|aze|ca|qc|us|uk|gb|fr|de|it|es|mx|pl|ie|au|nl|be|ar|az|al|br|ru|tr|in|ro|cz|hu|gr|pt|dk|se|fi|no|cl|co|pe)[\\)\\]]?\\s*[:|\\-]?\\s*(?:prime\\s+)?)",
        RegexOption.IGNORE_CASE,
    )

    private val groupCountryMap = mapOf(
        "uk" to "uk", "united kingdom" to "uk", "great britain" to "uk", "gb" to "uk",
        "fr" to "fr", "fra" to "fr", "france" to "fr",
        "us" to "us", "usa" to "us", "united states" to "us",
        "ca" to "ca", "canada" to "ca", "qc" to "ca", "quebec" to "ca",
        "de" to "de", "deu" to "de", "germany" to "de", "deutschland" to "de",
        "es" to "es", "esp" to "es", "spain" to "es", "espana" to "es",
        "it" to "it", "ita" to "it", "italy" to "it", "italia" to "it",
        "pl" to "pl", "pol" to "pl", "poland" to "pl", "polska" to "pl",
        "pt" to "pt", "prt" to "pt", "portugal" to "pt",
        "nl" to "nl", "nld" to "nl", "netherlands" to "nl",
        "be" to "be", "bel" to "be", "belgium" to "be",
        "au" to "au", "aus" to "au", "australia" to "au",
        "ie" to "ie", "irl" to "ie", "ireland" to "ie",
        "mx" to "mx", "mex" to "mx", "mexico" to "mx",
        "ar" to "ar", "arg" to "ar", "argentina" to "ar",
        "az" to "az", "aze" to "az", "azerbaijan" to "az",
        "al" to "al", "alb" to "al", "albania" to "al",
        "br" to "br", "bra" to "br", "brazil" to "br", "brasil" to "br",
        "ru" to "ru", "rus" to "ru", "russia" to "ru",
        "tr" to "tr", "tur" to "tr", "turkey" to "tr",
        "ro" to "ro", "rou" to "ro", "romania" to "ro",
        "in" to "in", "ind" to "in", "india" to "in",
        "cz" to "cz", "cze" to "cz",
        "hu" to "hu", "hun" to "hu",
        "gr" to "gr", "grc" to "gr", "greece" to "gr",
        "cl" to "cl", "chl" to "cl", "chile" to "cl",
        "co" to "co", "col" to "co", "colombia" to "co",
        "pe" to "pe", "per" to "pe", "peru" to "pe",
        "dk" to "dk", "dnk" to "dk", "denmark" to "dk",
        "se" to "se", "swe" to "se", "sweden" to "se",
        "no" to "no", "nor" to "no", "norway" to "no",
        "fi" to "fi", "fin" to "fi", "finland" to "fi",
    )

    private val nameCountryPrefixRegex = Regex(
        "^(?:[\\[(]([A-Za-z]{2,})[\\])]|([A-Za-z]{2,})\\s*[:|\\-])",
        RegexOption.IGNORE_CASE,
    )

    private val numberWordRegex = Regex("(?i)\\b(one|two|three|four|five|six|seven|eight|nine|ten|un|deux|trois|quatre)\\b")
    private val numberWordReplacements = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "un" to "1", "deux" to "2", "trois" to "3", "quatre" to "4",
    )

    internal fun normalizeNumberWords(s: String): String =
        numberWordRegex.replace(s) { match ->
            numberWordReplacements[match.value.lowercase()] ?: match.value
        }

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

    /** Ambiguous aliases remain absent from direct keys, but preserved under ambig: prefix for fallback. */
    fun buildAliasIndex(guideAliases: Map<String, Set<String>>): Map<String, String> {
        val index = mutableMapOf<String, String>()
        val ambiguous = mutableMapOf<String, MutableSet<String>>()
        guideAliases.forEach { (id, aliases) ->
            val baseKeys = aliases + id.guideMatchKeys()
            val region = country(id)
            val stripped = id.substringBefore('@')
            val idKeys = mutableSetOf(
                "exact_lower:" + id.lowercase(),
                "id:" + stripped.lowercase(),
                "nid:" + stripped.normalizedGuideKey(),
            )
            hyphenCallsignRegex.matchEntire(stripped)?.let { match ->
                val base = match.groupValues[1] + match.groupValues[2]
                idKeys.add("id:" + base.lowercase())
                idKeys.add("nid:" + base.normalizedGuideKey())
            }
            val keys = baseKeys + baseKeys.map { "country:$region:$it" } + idKeys
            keys.forEach { key ->
                if (key in ambiguous) {
                    ambiguous[key]?.add(id)
                } else {
                    val previous = index.putIfAbsent(key, id)
                    if (previous != null && previous != id) {
                        index.remove(key)
                        ambiguous[key] = mutableSetOf(previous, id)
                    }
                }
            }
        }
        ambiguous.forEach { (key, set) ->
            index["ambig:$key"] = set.joinToString("|")
        }
        return index
    }

    fun lookupGuidePrograms(
        guide: Map<String, List<Program>>,
        key: String,
        aliasIndex: Map<String, String> = emptyMap(),
    ): List<Program>? {
        if (key.isBlank()) return null
        guide[key]?.let { return it }
        val stripped = key.substringBefore('@')
        if (stripped != key) {
            guide[stripped]?.let { return it }
        }
        if (aliasIndex.isNotEmpty()) {
            val lowerKey = key.lowercase()
            aliasIndex["exact_lower:$lowerKey"]?.let { guide[it] }?.let { return it }
            val lowerStripped = stripped.lowercase()
            aliasIndex["id:$lowerStripped"]?.let { guide[it] }?.let { return it }
            aliasIndex["exact_lower:$lowerStripped"]?.let { guide[it] }?.let { return it }
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
        } else stationIds[providerId] ?: stationIds[channel.tvgId]
        reviewed?.let { exact(it)?.let { match -> return match } }

        extractLogoIdHint(channel.logoUrl)?.let { hint ->
            exact(hint)?.let { match ->
                if (compatibleCountry(channel, match)) return match
            }
        }

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
        val channelCountry = countryForChannel(channel)
        val fullMatches = names.mapNotNull {
            aliasIndex["country:$channelCountry:$it"] ?: aliasIndex[it]
        }.filter {
            compatibleCountry(channel, it) && !hasConflictingDirection(channel.name, it)
        }.distinct()
        if (fullMatches.size == 1) return fullMatches.single()
        if (fullMatches.size > 1) {
            disambiguateRegionalFeed(fullMatches, channel)?.let { return it }
            return null
        }

        // When multiple regional feeds collide on the same name, they are preserved under ambig:
        val ambiguousCandidates = names.flatMap { alias ->
            val ambigKey = if (channelCountry != null) {
                aliasIndex["ambig:country:$channelCountry:$alias"] ?: aliasIndex["ambig:$alias"]
            } else {
                aliasIndex["ambig:$alias"]
            }
            ambigKey?.split('|') ?: emptyList()
        }.distinct().filter {
            compatibleCountry(channel, it) && !hasConflictingDirection(channel.name, it)
        }
        if (ambiguousCandidates.isNotEmpty()) {
            disambiguateRegionalFeed(ambiguousCandidates, channel)?.let { return it }
        }

        val stations = listOfNotNull(channel.name, channel.tvgName).flatMap { name ->
            stationKeys(name)
        }.distinct()
        val stationMatches = stations.mapNotNull {
            aliasIndex["country:$channelCountry:$it"] ?: aliasIndex[it]
        }
            .filter { compatibleCountry(channel, it) && !hasConflictingDirection(channel.name, it) }
            .distinct()
        if (stationMatches.size == 1) return stationMatches.single()
        if (stationMatches.size > 1) {
            disambiguateRegionalFeed(stationMatches, channel)?.let { return it }
            return null
        }

        if (stationMatches.isEmpty() && stations.isNotEmpty()) {
            val ambigStations = stations.flatMap { st ->
                val ambigKey = if (channelCountry != null) {
                    aliasIndex["ambig:country:$channelCountry:$st"] ?: aliasIndex["ambig:$st"]
                } else {
                    aliasIndex["ambig:$st"]
                }
                ambigKey?.split('|') ?: emptyList()
            }.distinct().filter {
                compatibleCountry(channel, it) && !hasConflictingDirection(channel.name, it)
            }
            if (ambigStations.isNotEmpty()) {
                disambiguateRegionalFeed(ambigStations, channel)?.let { return it }
            }
        }
        return null
    }

    private val ignoredDisambiguationWords = setOf(
        "bbc", "itv", "channel", "france", "tele", "television", "radio",
        "news", "sports", "sport", "live", "plus", "extra", "hd", "fhd", "uhd",
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "un", "deux", "trois", "quatre", "the",
    )

    fun disambiguateRegionalFeed(candidates: List<String>, channel: Channel): String? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()

        val channelCountry = countryForChannel(channel)
        val channelLower = channel.name.lowercase()
        val channelTokens = channelLower.split(nonAlphaNumSplitRegex)
            .filter { it.length >= 3 && it !in ignoredDisambiguationWords }
        val channelKeys = channelAliases(channel)

        var foundSignal = false
        val candidateScores = candidates.map { candidate ->
            var score = 0
            val candidateLower = candidate.lowercase()
            val stripped = candidate.substringBefore('@').substringBeforeLast('.')
            val strippedLower = stripped.lowercase()

            // Exact base match: e.g. candidate "bbcone.uk" with channel "bbcone"
            if (channelKeys.contains(strippedLower) || channelKeys.contains(strippedLower.normalizedGuideKey())) {
                score += 100
                foundSignal = true
            }

            // Region token in channel name explicitly matches candidate
            val matchedToken = channelTokens.firstOrNull { token -> candidateLower.contains(token) }
            if (matchedToken != null) {
                score += 200
                foundSignal = true
            }

            // National / flagship feeds
            if ("national" in candidateLower || "main" in candidateLower) {
                score += 50
                foundSignal = true
            }

            when (channelCountry) {
                "uk" -> if ("london" in candidateLower) { score += 40; foundSignal = true }
                "fr" -> if ("paris" in candidateLower) { score += 40; foundSignal = true }
                "ca" -> if ("toronto" in candidateLower || "ottawa" in candidateLower) { score += 40; foundSignal = true }
                "us" -> if ("national" in candidateLower || "network" in candidateLower) { score += 40; foundSignal = true }
                "de" -> if ("berlin" in candidateLower) { score += 40; foundSignal = true }
                "es" -> if ("madrid" in candidateLower) { score += 40; foundSignal = true }
                "it" -> if ("roma" in candidateLower || "rome" in candidateLower) { score += 40; foundSignal = true }
                "au" -> if ("sydney" in candidateLower) { score += 40; foundSignal = true }
                "ie" -> if ("dublin" in candidateLower) { score += 40; foundSignal = true }
                "ar" -> if ("buenosaires" in candidateLower || "capital" in candidateLower) { score += 40; foundSignal = true }
                "br" -> if ("brasilia" in candidateLower || "saopaulo" in candidateLower) { score += 40; foundSignal = true }
                "al" -> if ("tirana" in candidateLower) { score += 40; foundSignal = true }
                "tr" -> if ("istanbul" in candidateLower || "ankara" in candidateLower) { score += 40; foundSignal = true }
            }

            // Quality tag preference
            val channelWantsHd = qualityTagRegex.containsMatchIn(channelLower)
            val candidateHasHd = "hd" in candidateLower
            if (channelWantsHd == candidateHasHd) {
                score += 10
            }

            // Shorter IDs indicate root/national feed over regional feeds
            score += (100 - candidate.length).coerceAtLeast(0)

            candidate to score
        }

        if (!foundSignal) return null

        val sorted = candidateScores.sortedByDescending { it.second }
        if (sorted.size >= 2 && sorted[0].second == sorted[1].second) {
            return null // Ambiguous tie
        }
        return sorted.firstOrNull()?.first
    }

    fun countryForChannel(channel: Channel): String? {
        country(channel.tvgId)?.let { return it }
        country(channel.id)?.let { return it }
        if (channel.group.isNotBlank()) {
            val tokens = channel.group.split(nonAlphaNumSplitRegex)
            for (token in tokens) {
                groupCountryMap[token.lowercase()]?.let { return it }
            }
        }
        val namePrefixMatch = nameCountryPrefixRegex.find(channel.name)
        if (namePrefixMatch != null) {
            val code = (namePrefixMatch.groupValues[1].ifEmpty { namePrefixMatch.groupValues[2] }).lowercase()
            groupCountryMap[code]?.let { return it }
        }
        return null
    }

    fun extractLogoIdHint(logoUrl: String?): String? {
        if (logoUrl.isNullOrBlank()) return null
        val filename = logoUrl.substringAfterLast('/').substringBefore('?')
        if (filename.endsWith(".png", ignoreCase = true) || filename.endsWith(".jpg", ignoreCase = true)) {
            val candidate = filename.substringBeforeLast('.')
            if ('.' in candidate && !candidate.startsWith("logo", ignoreCase = true) && !candidate.startsWith("tv", ignoreCase = true)) {
                return candidate
            }
        }
        return null
    }

    private fun stationKeys(name: String): List<String> {
        // A callsign alone cannot distinguish digital subchannels.
        if (subchannelRegex.containsMatchIn(name)) return emptyList()
        return callSign.findAll(name)
            .filter {
                it.groupValues[1] !in setOf(
                    "WEST", "KIDS", "WITH", "WORK", "CITY", "CNBC", "CCTV", "CARS",
                    "COOL", "CLUB", "CAMP", "CHAT", "COOK", "NEWS",
                )
            }
            .map { "station:" + it.groupValues[1].lowercase() }.toList()
    }

    private fun country(id: String?): String? = id?.substringBefore('@')?.substringAfterLast('.', "")
        ?.lowercase()?.trimEnd { it.isDigit() }?.takeIf { it.length == 2 && it.all(Char::isLetter) }

    private fun compatibleCountry(channel: Channel, guideId: String): Boolean {
        val source = countryForChannel(channel)
        val target = country(guideId)
        return source == null || target == null || source == target
    }

    private fun hasConflict(channelName: String, guideIdOrName: String): Boolean =
        hasConflictingNumber(channelName, guideIdOrName) ||
            hasConflictingDirection(channelName, guideIdOrName)

    fun hasConflictingNumber(a: String, b: String): Boolean {
        val digitsA = normalizeNumberWords(a).filter { it.isDigit() }
        val digitsB = normalizeNumberWords(b).filter { it.isDigit() }
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
        GuideMatcher.hyphenCallsignRegex.matchEntire(stripped)?.let { match ->
            val base = match.groupValues[1] + match.groupValues[2]
            add(base)
            add(base.normalizedGuideKey())
        }
    }.filterTo(mutableSetOf()) { it.isNotEmpty() }
}

private val nonAlphaNumSplitRegex = Regex("[^A-Za-z0-9]+")
private val qualityTagRegex = Regex("\\b(hd|fhd|uhd|sd|us|usa|4k|1080p|720p|50fps|hevc)\\b")
private val nonLowerAlphaNumRegex = Regex("[^a-z0-9]+")

internal fun String.normalizedGuideTokens(): Set<String> {
    return split(nonAlphaNumSplitRegex)
        .map { it.normalizedGuideKey() }
        .filter { it.length >= 3 && !it.all { ch -> ch.isDigit() } }
        .toSet()
}

internal fun String.normalizedGuideKey(): String =
    lowercase()
        .replace(qualityTagRegex, " ")
        .replace(nonLowerAlphaNumRegex, "")

/** Remove provider labels and quality markers, retaining every meaningful word. */
private fun String.fullGuideNameKeys(): Set<String> = buildSet {
    add(normalizedGuideKey())
    val withNumbers = GuideMatcher.normalizeNumberWords(this@fullGuideNameKeys)
    if (withNumbers != this@fullGuideNameKeys) {
        val normalized = withNumbers.normalizedGuideKey()
        if (normalized.isNotEmpty()) add(normalized)
    }
    val prefixStripped = replace(GuideMatcher.prefixStripRegex, "").trim()
    if (prefixStripped != this@fullGuideNameKeys) {
        val normalized = prefixStripped.normalizedGuideKey()
        if (normalized.isNotEmpty()) {
            add(normalized)
            val strippedWithNumbers = GuideMatcher.normalizeNumberWords(prefixStripped)
            if (strippedWithNumbers != prefixStripped) {
                val numNorm = strippedWithNumbers.normalizedGuideKey()
                if (numNorm.isNotEmpty()) add(numNorm)
            }
        }
    }
    // XMLTV often prefixes a complete name with "US:", "Xumo:" or state/city pipes.
    val display = substringAfterLast('|').substringAfterLast(':').trim()
    add(display.normalizedGuideKey())
    val displayWithNumbers = GuideMatcher.normalizeNumberWords(display)
    if (displayWithNumbers != display) {
        val numNorm = displayWithNumbers.normalizedGuideKey()
        if (numNorm.isNotEmpty()) add(numNorm)
    }
    val lcnStripped = display.replace(GuideMatcher.leadingLcnRegex, "").trim()
    if (lcnStripped != display) {
        val lcnNorm = lcnStripped.normalizedGuideKey()
        if (lcnNorm.isNotEmpty()) {
            add(lcnNorm)
            val lcnWithNumbers = GuideMatcher.normalizeNumberWords(lcnStripped)
            if (lcnWithNumbers != lcnStripped) {
                val numNorm = lcnWithNumbers.normalizedGuideKey()
                if (numNorm.isNotEmpty()) add(numNorm)
            }
        }
    }
    val cityStripped = lcnStripped.replace(qualityTagRegex, " ").trim()
        .replace(GuideMatcher.regionalCitySuffixRegex, "").trim()
    if (cityStripped.isNotEmpty() && cityStripped != lcnStripped) {
        val cityNorm = cityStripped.normalizedGuideKey()
        if (cityNorm.isNotEmpty()) {
            add(cityNorm)
            val cityWithNumbers = GuideMatcher.normalizeNumberWords(cityStripped)
            if (cityWithNumbers != cityStripped) {
                val numNorm = cityWithNumbers.normalizedGuideKey()
                if (numNorm.isNotEmpty()) add(numNorm)
            }
        }
    }
}.filterTo(linkedSetOf()) { it.isNotEmpty() }
