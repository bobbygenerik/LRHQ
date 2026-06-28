package com.livingroomhq.translate

import com.livingroomhq.core.data.net.LrhqHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class SubtitleTrack(
    val language: String,
    val name: String,
    val url: String,
)

class SubtitleFetcher {

    suspend fun fetchSubtitleTracks(hlsMasterUrl: String): List<SubtitleTrack> =
        withContext(Dispatchers.IO) {
            val manifest = httpGet(hlsMasterUrl) ?: return@withContext emptyList()
            parseSubtitleRenditions(manifest, hlsMasterUrl)
        }

    suspend fun fetchCues(subtitleUrl: String): List<SubtitleCue> =
        withContext(Dispatchers.IO) {
            val content = httpGet(subtitleUrl) ?: return@withContext emptyList()
            if (subtitleUrl.contains(".m3u8")) {
                val segments = parseSubtitlePlaylist(content, subtitleUrl)
                segments.flatMap { segmentUrl ->
                    val vtt = httpGet(segmentUrl) ?: return@flatMap emptyList()
                    parseVtt(vtt)
                }
            } else {
                parseVtt(content)
            }
        }

    private fun parseSubtitleRenditions(manifest: String, baseUrl: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        val regex = Regex(
            """#EXT-X-MEDIA:.*?TYPE=SUBTITLES.*?NAME="([^"]*)".*?LANGUAGE="([^"]*)".*?URI="([^"]*)""""
        )
        for (match in regex.findAll(manifest)) {
            val name = match.groupValues[1]
            val language = match.groupValues[2]
            val uri = match.groupValues[3]
            tracks.add(
                SubtitleTrack(
                    language = language,
                    name = name,
                    url = resolveUrl(baseUrl, uri),
                )
            )
        }
        return tracks
    }

    private fun parseSubtitlePlaylist(playlist: String, baseUrl: String): List<String> {
        return playlist.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { resolveUrl(baseUrl, it.trim()) }
    }

    fun parseVtt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.split(Regex("\n\n+"))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val timingLine = lines.firstOrNull { it.contains("-->") } ?: continue
            val timing = parseVttTiming(timingLine) ?: continue
            val text = lines.dropWhile { it.contains("-->") }.joinToString(" ")
            if (text.isNotBlank()) {
                cues.add(SubtitleCue(timing.first, timing.second, text))
            }
        }
        return cues
    }

    private fun parseVttTiming(line: String): Pair<Long, Long>? {
        val parts = line.split("-->").map { it.trim() }
        if (parts.size != 2) return null
        val start = parseTimestamp(parts[0]) ?: return null
        val end = parseTimestamp(parts[1].split(" ").first()) ?: return null
        return start to end
    }

    private fun parseTimestamp(ts: String): Long? {
        val parts = ts.split(":")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parseSeconds(parts[2]) ?: return null
                h * 3600000 + m * 60000 + s
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parseSeconds(parts[1]) ?: return null
                m * 60000 + s
            }
            else -> null
        }
    }

    private fun parseSeconds(s: String): Long? {
        val parts = s.split(".")
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = if (parts.size > 1) {
            val f = parts[1].take(3).padEnd(3, '0').toLongOrNull() ?: 0L
            f
        } else 0L
        return whole * 1000 + frac
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http")) return relative
        val baseDir = base.substringBeforeLast('/')
        return "$baseDir/$relative"
    }

    private fun httpGet(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LRHQ SubtitleFetcher")
                .build()
            LrhqHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }
}
