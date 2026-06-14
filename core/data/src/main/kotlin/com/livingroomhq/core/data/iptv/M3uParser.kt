package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Channel
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Minimal M3U/M3U8 IPTV playlist parser. Channel ids prefer `tvg-id` and fall
 * back to the stream URL so favorites/recents stay stable across reloads.
 * Parses streamingly from an InputStream to conserve memory.
 */
object M3uParser {

    private val attrRegex = Regex("([\\w-]+)=(?:\"([^\"]*)\"|'([^']*)')")

    fun parse(inputStream: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingExtinf: String? = null

        inputStream.bufferedReader(UTF_8).use { reader ->
            for (raw in reader.lineSequence()) {
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
                                tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
                                tvgChno = attrs["tvg-chno"]?.takeIf { it.isNotBlank() },
                            )
                        }
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
