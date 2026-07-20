package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Program
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Minimal XMLTV electronic-programme-guide parser. Maps `<programme>` elements
 * to [Program]s. Uses a memory-efficient streaming [XmlPullParser] to support
 * large guides without consuming excessive memory (O(1) memory complexity).
 */
object XmltvParser {

    suspend fun parse(
        inputStream: InputStream,
        onChannelParsed: (id: String, displayNames: List<String>) -> Unit = { _, _ -> },
        onProgramParsed: suspend (Program) -> Unit,
    ) {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        inputStream.use { stream ->
            parser.setInput(stream, "UTF-8")
            var eventType = parser.eventType
            var xmltvChannelId: String? = null
            var xmltvChannelNames = mutableListOf<String>()
            var programmeChannelId: String? = null
            var startMillis: Long? = null
            var endMillis: Long? = null
            var title: String? = null
            var description: String? = null
            var artworkUrl: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name.equals("channel", ignoreCase = true)) {
                            xmltvChannelId = parser.getAttributeValue(null, "id")?.trim()
                            xmltvChannelNames = mutableListOf()
                        } else if (name.equals("programme", ignoreCase = true)) {
                            programmeChannelId = parser.getAttributeValue(null, "channel")?.trim()
                            startMillis = parseTime(parser.getAttributeValue(null, "start").orEmpty())
                            endMillis = parseTime(parser.getAttributeValue(null, "stop").orEmpty())
                            title = null
                            description = null
                            artworkUrl = null
                        } else if (programmeChannelId != null) {
                            when (name.lowercase()) {
                                "title" -> title = parser.readElementText().trim()
                                "desc" -> description = parser.readElementText().trim()
                                "icon", "image", "thumb", "poster" -> {
                                    val src = parser.getAttributeValue(null, "src")?.trim()
                                        ?: parser.readElementText().trim()
                                    normalizeArtworkUrl(src)?.let { artworkUrl = it }
                                }
                            }
                        } else if (xmltvChannelId != null && name.equals("display-name", ignoreCase = true)) {
                            parser.readElementText().trim().takeIf { it.isNotEmpty() }?.let { xmltvChannelNames += it }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name.equals("channel", ignoreCase = true)) {
                            xmltvChannelId?.let { id -> onChannelParsed(id, xmltvChannelNames) }
                            xmltvChannelId = null
                            xmltvChannelNames = mutableListOf()
                        } else if (name.equals("programme", ignoreCase = true)) {
                            if (programmeChannelId != null && startMillis != null && endMillis != null && title != null) {
                                val program = Program(
                                    channelId = programmeChannelId,
                                    title = title,
                                    description = description.orEmpty(),
                                    startMillis = startMillis,
                                    endMillis = endMillis,
                                    artworkUrl = artworkUrl,
                                )
                                onProgramParsed(program)
                            }
                            programmeChannelId = null
                        }
                    }
                }
                eventType = parser.next()
            }
        }
    }

    /**
     * Like [XmlPullParser.nextText] but tolerates nested markup (e.g.
     * `<title>News <sub>live</sub></title>`), concatenating every text node
     * until the element's own end tag. Leaves the parser positioned on that
     * end tag, matching nextText's post-condition.
     */
    private fun XmlPullParser.readElementText(): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT -> sb.append(text)
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    /**
     * XMLTV time: `YYYYMMDDHHMMSS` optionally followed by a ` ±HHMM` offset
     * (UTC if absent). Parsed with plain integer math — SimpleDateFormat costs
     * two Date allocations plus a full pattern parse per programme, which
     * dominates ingest time on 100k+ programme guides.
     */
    private fun parseTime(raw: String): Long? {
        val s = raw.trim()
        if (s.length < 14) return null
        val year = s.digitsOrNull(0, 4) ?: return null
        val month = s.digitsOrNull(4, 6) ?: return null
        val day = s.digitsOrNull(6, 8) ?: return null
        val hour = s.digitsOrNull(8, 10) ?: return null
        val minute = s.digitsOrNull(10, 12) ?: return null
        val second = s.digitsOrNull(12, 14) ?: return null
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null

        val utcMillis =
            (epochDays(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second) * 1_000L
        return utcMillis - parseZoneOffsetMillis(s.substring(14).trim())
    }

    /** `±HHMM` → offset millis; anything else (including empty) is treated as UTC. */
    private fun parseZoneOffsetMillis(zone: String): Long {
        if (zone.length != 5) return 0L
        val sign = when (zone[0]) {
            '+' -> 1L
            '-' -> -1L
            else -> return 0L
        }
        val hours = zone.digitsOrNull(1, 3) ?: return 0L
        val minutes = zone.digitsOrNull(3, 5) ?: return 0L
        return sign * (hours * 3_600L + minutes * 60L) * 1_000L
    }

    private fun String.digitsOrNull(from: Int, until: Int): Int? {
        var value = 0
        for (i in from until until) {
            val c = this[i]
            if (c !in '0'..'9') return null
            value = value * 10 + (c - '0')
        }
        return value
    }

    /** Civil date → days since 1970-01-01 (Howard Hinnant's algorithm). */
    private fun epochDays(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    fun normalizeArtworkUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> null
        }
    }
}
