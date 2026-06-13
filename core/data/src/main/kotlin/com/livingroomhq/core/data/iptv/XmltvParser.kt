package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Program
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal XMLTV electronic-programme-guide parser. Maps `<programme>` elements
 * to [Program]s keyed by their `channel` attribute — which is the channel's
 * `tvg-id`, the same id [M3uParser] assigns — so a loaded guide lines up with
 * loaded channels. Pure (String in, Map out) and JVM-unit-testable via DOM.
 *
 * Plain XML only; gzipped `.xml.gz` guides must be decompressed before calling.
 * External DTD/entity loading is disabled (XMLTV's `<!DOCTYPE>` would otherwise
 * try to fetch xmltv.dtd, and to guard against XXE).
 */
object XmltvParser {

    fun parse(xml: String): Map<String, List<Program>> {
        if (xml.isBlank()) return emptyMap()

        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isValidating = false
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        }.getOrNull() ?: return emptyMap()

        val result = LinkedHashMap<String, MutableList<Program>>()
        val nodes = doc.getElementsByTagName("programme")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val channel = el.getAttribute("channel").takeIf { it.isNotBlank() } ?: continue
            val start = parseTime(el.getAttribute("start")) ?: continue
            val stop = parseTime(el.getAttribute("stop")) ?: continue
            val title = childText(el, "title") ?: continue
            val desc = childText(el, "desc").orEmpty()
            val artworkUrl = childIconUrl(el)
            result.getOrPut(channel) { mutableListOf() }
                .add(Program(channel, title, desc, start, stop, artworkUrl))
        }
        return result.mapValues { (_, programmes) -> programmes.sortedBy { it.startMillis } }
    }

    private fun childText(el: Element, tag: String): String? {
        val children = el.getElementsByTagName(tag)
        if (children.length == 0) return null
        return children.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun childIconUrl(el: Element): String? {
        val children = el.getElementsByTagName("icon")
        for (i in 0 until children.length) {
            val icon = children.item(i) as? Element ?: continue
            val src = icon.getAttribute("src").trim()
            if (src.startsWith("https://", ignoreCase = true) || src.startsWith("http://", ignoreCase = true)) {
                return src
            }
        }
        return null
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
