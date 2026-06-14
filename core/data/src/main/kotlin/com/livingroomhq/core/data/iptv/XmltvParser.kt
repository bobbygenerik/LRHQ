package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Program
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Minimal XMLTV electronic-programme-guide parser. Maps `<programme>` elements
 * to [Program]s. Uses a memory-efficient streaming [XmlPullParser] to support
 * large guides without consuming excessive memory (O(1) memory complexity).
 */
object XmltvParser {

    fun parse(
        inputStream: InputStream,
        onChannelParsed: (id: String, displayNames: List<String>) -> Unit = { _, _ -> },
        onProgramParsed: (Program) -> Unit,
    ) {
        try {
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
                                    "title" -> title = parser.nextText()?.trim()
                                    "desc" -> description = parser.nextText()?.trim()
                                    "icon" -> {
                                        val src = parser.getAttributeValue(null, "src")?.trim()
                                        if (src != null && (src.startsWith("https://", ignoreCase = true) || src.startsWith("http://", ignoreCase = true))) {
                                            artworkUrl = src
                                        }
                                    }
                                }
                            } else if (xmltvChannelId != null && name.equals("display-name", ignoreCase = true)) {
                                parser.nextText()?.trim()?.takeIf { it.isNotEmpty() }?.let { xmltvChannelNames += it }
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
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    /** XMLTV time: `YYYYMMDDHHMMSS` optionally followed by a ` +ZZZZ` offset (UTC if absent). */
    private fun parseTime(raw: String): Long? {
        val s = raw.trim()
        if (s.length < 14) return null
        return runCatching {
            val datePart = s.substring(0, 14)
            val zonePart = s.substring(14).trim()
            if (zonePart.isNotEmpty()) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse("$datePart $zonePart")?.time
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(datePart)?.time
            }
        }.getOrNull()
    }
}
