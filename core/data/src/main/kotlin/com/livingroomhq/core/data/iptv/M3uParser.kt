package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Channel

/**
 * Minimal M3U/M3U8 IPTV playlist parser. Channel ids prefer `tvg-id` and fall
 * back to the stream URL so favorites/recents stay stable across reloads.
 */
object M3uParser {

    private val attrRegex = Regex("([\\w-]+)=(?:\"([^\"]*)\"|'([^']*)')")

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingExtinf: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingExtinf = line
                line.isEmpty() || line.startsWith("#") -> Unit // headers, comments
                else -> {
                    val extinf = pendingExtinf
                    pendingExtinf = null
                    if (extinf != null) {
                        val attrs = attrRegex.findAll(extinf)
                            .associate { m ->
                                m.groupValues[1].lowercase() to (m.groupValues[2].ifEmpty { m.groupValues[3] })
                            }
                        val name = extinf.displayName().ifEmpty { line }
                        channels += Channel(
                            id = attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: line,
                            number = channels.size + 1,
                            name = name,
                            group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Other",
                            streamUrl = line,
                            logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        )
                    }
                }
            }
        }
        return channels
    }

    private fun String.displayName(): String {
        var inSingleQuote = false
        var inDoubleQuote = false
        forEachIndexed { index, char ->
            when (char) {
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                ',' -> if (!inSingleQuote && !inDoubleQuote) {
                    return substring(index + 1).trim()
                }
            }
        }
        return ""
    }
}
