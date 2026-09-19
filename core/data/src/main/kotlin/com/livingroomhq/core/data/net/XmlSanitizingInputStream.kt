package com.livingroomhq.core.data.net

import java.io.InputStream

/**
 * InputStream wrapper that escapes bare `&` characters that are not part of a
 * valid XML entity reference (`&name;`, `&#digits;`, or `&#xhex;`).  Fixes
 * "unterminated entity ref" errors from IPTV providers that emit malformed
 * XMLTV (e.g. `AT&T` instead of `AT&amp;T`).
 */
class XmlSanitizingInputStream(
    private val delegate: InputStream,
) : InputStream() {

    private val amp = "&amp;".toByteArray()
    private var ampOffset = -1

    override fun read(): Int {
        if (ampOffset in amp.indices) return amp[ampOffset++].toInt().also {
            if (ampOffset >= amp.size) ampOffset = -1
        }
        val b = delegate.read()
        if (b != '&'.code) return b

        val ahead = IntArray(20)
        ahead[0] = delegate.read()
        if (ahead[0] == -1) return b

        val isEntity = when {
            ahead[0] == '#'.code -> {
                val hex = ahead[1] == 'x'.code || ahead[1] == 'X'.code
                val start = if (hex) 2 else 1
                var i = start
                while (i < ahead.size) {
                    val c = delegate.read()
                    if (c == -1) { ahead[i] = c; break }
                    ahead[i] = c
                    if (c == ';'.code) break
                    i++
                }
                i < ahead.size && ahead[i] == ';'.code
            }
            ahead[0] == ';'.code -> false
            else -> {
                var i = 1
                while (i < ahead.size) {
                    val c = delegate.read()
                    if (c == -1) { ahead[i] = c; break }
                    ahead[i] = c
                    if (c == ';'.code || !c.isXmlNameChar()) break
                    i++
                }
                ahead[i] == ';'.code
            }
        }

        return if (isEntity) {
            b
        } else {
            ampOffset = 1
            amp[0].toInt()
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val first = read()
        if (first == -1) return -1
        b[off] = first.toByte()
        var count = 1
        while (count < len) {
            val next = read()
            if (next == -1) break
            b[off + count] = next.toByte()
            count++
        }
        return count
    }

    override fun close() = delegate.close()
}

private fun Int.isXmlNameChar(): Boolean =
    this in 'a'.code..'z'.code ||
        this in 'A'.code..'Z'.code ||
        this in '0'.code..'9'.code ||
        this == '.'.code || this == '-'.code ||
        this == '_'.code || this == ':'.code
