package com.livingroomhq.core.data.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Display-only cleanup for provider names. Raw names stay untouched in the
 * model (guide matching and ids depend on them); UI should render these.
 *
 * - Strips country prefixes ("PT: ", "UK | ", "[CA] ") and quality tags
 *   ("4K ", " HD", " FHD", " UHD", " HEVC", "1080p").
 * - Restores broadcaster acronyms that providers title-case ("Cbc" -> "CBC").
 * - Never returns blank: falls back to the trimmed raw name.
 */
object DisplayNames {

    private val channelCache = ConcurrentHashMap<String, String>()
    private val titleCache = ConcurrentHashMap<String, String>()

    private const val COUNTRY_CODES =
        "PT|BR|UK|GB|US|USA|CA|FR|ES|DE|IT|IE|NL|BE|CH|AT|PL|TR|GR|RO|AR|MX|IN|AU|NZ|LAT|LATAM|EN|EU|AF|ARAB|AR"

    private val countryPrefix = Regex(
        """^\s*(?:\[(?:$COUNTRY_CODES)]|\((?:$COUNTRY_CODES)\)|(?:$COUNTRY_CODES)\s*[:|\-–])\s*""",
        RegexOption.IGNORE_CASE,
    )

    private const val QUALITY = """4K|8K|UHD|FHD|HD|SD|HEVC|H\.?265|H\.?264|1080[pi]?|720p|2160p|ᴴᴰ|ᵁᴴᴰ|ᶠᴴᴰ"""
    private val leadingQuality = Regex("""^\s*[\[(]?(?:$QUALITY)[\])]?\s*[:|\-–]?\s+""", RegexOption.IGNORE_CASE)
    // Needs a separator before the tag so words like "LSD" keep their ending.
    private val trailingQuality = Regex("""(?:\s+|\s*[\[(|\-–]\s*)(?:$QUALITY)[\])]?\s*$""", RegexOption.IGNORE_CASE)

    /** Acronyms that contain vowels, so the no-vowel heuristic can't catch them. */
    private val knownAcronyms = setOf(
        "TV", "TVI", "SIC", "ITV", "ESPN", "HBO", "AMC", "ABC", "TVE", "RAI", "ARD", "ORF", "NHK", "SBS",
        "AXN", "MTV", "TLC", "UKTV", "RTE", "ATV", "TSN", "CTV", "TNT", "HGTV", "NFL", "NBA", "MLB", "NHL",
        "UFC", "WWE", "BET", "PBS", "OWN", "TBS", "USA", "ZDF", "SVT", "NRK", "RTL",
        "EWTN", "CNBC", "MSNBC", "TVO", "APTN", "CPAC", "RDI", "TFO", "TV5", "AMI",
    )

    /** Vowel-less words that are real words/abbreviations, not acronyms. */
    private val notAcronyms = setOf("MR", "MRS", "MS", "DR", "JR", "SR", "ST", "PT", "LTD", "VS")

    private val tokenRegex = Regex("""[A-Za-z]+""")

    fun channel(raw: String): String = channelCache.getOrPut(raw) { cleanChannel(raw) }

    fun title(raw: String): String = titleCache.getOrPut(raw) { cleanTitle(raw) }

    private fun cleanChannel(raw: String): String {
        var s = raw.trim()
        s = stripRepeatedly(s, countryPrefix)
        s = stripRepeatedly(s, leadingQuality)
        s = stripRepeatedly(s, trailingQuality)
        s = fixAcronyms(s).replace(Regex("""\s{2,}"""), " ").trim()
        return s.ifBlank { raw.trim() }
    }

    private fun cleanTitle(raw: String): String {
        var s = raw.trim()
        s = stripRepeatedly(s, countryPrefix)
        s = stripRepeatedly(s, leadingQuality)
        s = stripRepeatedly(s, trailingQuality)
        s = s.replace(Regex("""\s{2,}"""), " ").trim()
        return s.ifBlank { raw.trim() }
    }

    private fun stripRepeatedly(input: String, regex: Regex): String {
        var s = input
        repeat(3) {
            val next = regex.replace(s, "")
            if (next == s || next.isBlank()) return s
            s = next
        }
        return s
    }

    private fun fixAcronyms(s: String): String = tokenRegex.replace(s) { m ->
        val word = m.value
        val upper = word.uppercase()
        val isTitleOrLower = word == word.lowercase() ||
            (word[0].isUpperCase() && word.substring(1) == word.substring(1).lowercase())
        when {
            word == upper -> word
            !isTitleOrLower -> word
            upper in notAcronyms -> word
            upper in knownAcronyms -> upper
            word.length in 2..4 && upper.none { it in "AEIOUY" } -> upper
            else -> word
        }
    }
}

/** Cleaned name for display; [Channel.name] stays raw for matching. */
val Channel.displayName: String get() = DisplayNames.channel(name)

val VodItem.displayTitle: String get() = DisplayNames.title(title)

val SeriesItem.displayTitle: String get() = DisplayNames.title(title)

private val credentialParam = Regex("""(?i)([?&](?:username|user|password|pass|pwd|token|key)=)([^&#\s]+)""")
private val userInfo = Regex("""(://)([^/@\s:]+):([^/@\s]+)@""")

/**
 * Masks credential values in a URL with "•", keeping the same length so text
 * field cursor offsets still line up. Everything else is left readable.
 */
fun maskUrlCredentials(url: String): String {
    var s = credentialParam.replace(url) { m -> m.groupValues[1] + "•".repeat(m.groupValues[2].length) }
    s = userInfo.replace(s) { m ->
        m.groupValues[1] + "•".repeat(m.groupValues[2].length) + ":" + "•".repeat(m.groupValues[3].length) + "@"
    }
    return s
}
