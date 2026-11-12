package com.livingroomhq.core.data.net

import java.io.InputStream
import java.io.PushbackInputStream

/**
 * InputStream wrapper that escapes bare `&` characters that are not part of a
 * valid XML entity reference (`&name;`, `&#digits;`, or `&#xhex;`). Fixes
 * "unterminated entity ref" errors from IPTV providers that emit malformed
 * XMLTV (e.g. `AT&T` instead of `AT&amp;T`).
 *
 * Uses a PushbackInputStream so all peeked lookahead bytes are pushed back
 * and processed through standard stream reading, guaranteeing zero byte loss.
 */
class XmlSanitizingInputStream(
    delegate: InputStream,
) : InputStream() {

    private val input = PushbackInputStream(delegate, PUSHBACK_BUFFER_SIZE)

    // Expansion bytes for escaping bare '&' -> '&' + 'a' + 'm' + 'p' + ';'
    private val ampExpansion = byteArrayOf('a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), ';'.code.toByte())
    private var expansionIndex = -1

    override fun read(): Int {
        if (expansionIndex in ampExpansion.indices) {
            val b = ampExpansion[expansionIndex++].toInt() and 0xFF
            if (expansionIndex >= ampExpansion.size) {
                expansionIndex = -1
            }
            return b
        }

        val b = input.read()
        if (b != '&'.code) return b

        // Peek ahead to check if this is a valid XML entity
        val peekBuffer = ByteArray(MAX_PEEK)
        var peekCount = 0

        while (peekCount < MAX_PEEK) {
            val c = input.read()
            if (c == -1) break
            peekBuffer[peekCount++] = c.toByte()
            if (c == ';'.code) break
            // Stop peeking on tag delimiters, another ampersand, or whitespace
            if (c == '<'.code || c == '>'.code || c == '&'.code || c.toChar().isWhitespace()) {
                break
            }
        }

        val isEntity = isValidEntity(peekBuffer, peekCount)

        // Push back all peeked bytes so they are read in normal sequence
        if (peekCount > 0) {
            input.unread(peekBuffer, 0, peekCount)
        }

        return if (isEntity) {
            // Valid entity: emit '&' and let the peeked entity characters be read next
            '&'.code
        } else {
            // Bare '&': emit '&', and arm expansion for 'amp;'
            expansionIndex = 0
            '&'.code
        }
    }

    private fun isValidEntity(bytes: ByteArray, len: Int): Boolean {
        if (len == 0 || bytes[len - 1] != ';'.code.toByte()) return false
        if (len < 2) return false

        return if (bytes[0] == '#'.code.toByte()) {
            // Numeric entity
            if (len < 3) return false
            if (bytes[1] == 'x'.code.toByte() || bytes[1] == 'X'.code.toByte()) {
                // Hex: &#x[0-9a-fA-F]+;
                if (len < 4) return false
                for (i in 2 until len - 1) {
                    val c = bytes[i].toInt().toChar()
                    if (c !in '0'..'9' && c !in 'a'..'f' && c !in 'A'..'F') return false
                }
                true
            } else {
                // Decimal: &# [0-9]+;
                for (i in 1 until len - 1) {
                    val c = bytes[i].toInt().toChar()
                    if (c !in '0'..'9') return false
                }
                true
            }
        } else {
            // Named entity: &[a-zA-Z_:][a-zA-Z0-9._:-]*;
            if (!bytes[0].toInt().isXmlNameStartChar()) return false
            for (i in 1 until len - 1) {
                if (!bytes[i].toInt().isXmlNameChar()) return false
            }
            true
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        var written = 0
        while (expansionIndex in ampExpansion.indices && written < len) {
            b[off + written++] = ampExpansion[expansionIndex++]
            if (expansionIndex >= ampExpansion.size) {
                expansionIndex = -1
                break
            }
        }
        if (written == len) return written

        val remainingToRead = minOf(len - written, PUSHBACK_BUFFER_SIZE)
        val readCount = input.read(b, off + written, remainingToRead)
        if (readCount == -1) {
            return if (written == 0) -1 else written
        }

        val startIdx = off + written
        val endIdx = startIdx + readCount
        var ampIdx = -1
        for (i in startIdx until endIdx) {
            if (b[i] == '&'.code.toByte()) {
                ampIdx = i
                break
            }
        }

        if (ampIdx == -1) {
            return written + readCount
        }

        val unreadCount = endIdx - ampIdx
        input.unread(b, ampIdx, unreadCount)

        val validWrittenBeforeAmp = ampIdx - off
        if (validWrittenBeforeAmp > 0) {
            return validWrittenBeforeAmp
        }

        val single = read()
        if (single == -1) return if (written == 0) -1 else written
        b[off] = single.toByte()
        return 1
    }

    override fun close() = input.close()

    companion object {
        private const val MAX_PEEK = 32
        private const val PUSHBACK_BUFFER_SIZE = 65536
    }
}

private fun Int.isXmlNameStartChar(): Boolean =
    this in 'a'.code..'z'.code ||
        this in 'A'.code..'Z'.code ||
        this == '_'.code || this == ':'.code

private fun Int.isXmlNameChar(): Boolean =
    isXmlNameStartChar() ||
        this in '0'.code..'9'.code ||
        this == '.'.code || this == '-'.code
