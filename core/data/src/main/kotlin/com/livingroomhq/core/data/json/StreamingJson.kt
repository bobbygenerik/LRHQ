package com.livingroomhq.core.data.json

import java.io.Closeable
import java.io.Flushable
import java.io.Reader
import java.io.Writer

class StreamingJsonWriter(private val out: Writer) : Closeable, Flushable {
    private var scopeStack = IntArray(32)
    private var scopeDepth = 0

    companion object {
        private const val EMPTY_ARRAY = 1
        private const val NONEMPTY_ARRAY = 2
        private const val EMPTY_OBJECT = 3
        private const val DANGLING_NAME = 4
        private const val NONEMPTY_OBJECT = 5
    }

    private fun pushScope(scope: Int) {
        if (scopeDepth == scopeStack.size) {
            scopeStack = scopeStack.copyOf(scopeStack.size * 2)
        }
        scopeStack[scopeDepth++] = scope
    }

    private fun peekScope(): Int = if (scopeDepth > 0) scopeStack[scopeDepth - 1] else 0

    private fun replaceScope(scope: Int) {
        scopeStack[scopeDepth - 1] = scope
    }

    private fun beforeValue() {
        when (val scope = peekScope()) {
            EMPTY_ARRAY -> replaceScope(NONEMPTY_ARRAY)
            NONEMPTY_ARRAY -> out.write(",")
            DANGLING_NAME -> {
                out.write(":")
                replaceScope(NONEMPTY_OBJECT)
            }
            0 -> {}
            else -> throw IllegalStateException("Nesting problem in JSON writer: scope $scope")
        }
    }

    fun beginArray(): StreamingJsonWriter {
        beforeValue()
        pushScope(EMPTY_ARRAY)
        out.write("[")
        return this
    }

    fun endArray(): StreamingJsonWriter {
        scopeDepth--
        out.write("]")
        return this
    }

    fun beginObject(): StreamingJsonWriter {
        beforeValue()
        pushScope(EMPTY_OBJECT)
        out.write("{")
        return this
    }

    fun endObject(): StreamingJsonWriter {
        scopeDepth--
        out.write("}")
        return this
    }

    fun name(name: String): StreamingJsonWriter {
        when (val scope = peekScope()) {
            EMPTY_OBJECT -> {}
            NONEMPTY_OBJECT -> out.write(",")
            else -> throw IllegalStateException("Nesting problem: name outside object (scope $scope)")
        }
        replaceScope(DANGLING_NAME)
        writeEscapedString(name)
        return this
    }

    fun value(value: String?): StreamingJsonWriter {
        beforeValue()
        if (value == null) {
            out.write("null")
        } else {
            writeEscapedString(value)
        }
        return this
    }

    fun value(value: Int): StreamingJsonWriter {
        beforeValue()
        out.write(value.toString())
        return this
    }

    fun value(value: Long): StreamingJsonWriter {
        beforeValue()
        out.write(value.toString())
        return this
    }

    fun value(value: Boolean): StreamingJsonWriter {
        beforeValue()
        out.write(if (value) "true" else "false")
        return this
    }

    private fun writeEscapedString(value: String) {
        out.write("\"")
        for (i in 0 until value.length) {
            when (val c = value[i]) {
                '\\' -> out.write("\\\\")
                '"' -> out.write("\\\"")
                '\b' -> out.write("\\b")
                '\u000C' -> out.write("\\f")
                '\n' -> out.write("\\n")
                '\r' -> out.write("\\r")
                '\t' -> out.write("\\t")
                else -> {
                    if (c < ' ') {
                        out.write(String.format("\\u%04x", c.code))
                    } else {
                        out.write(c.code)
                    }
                }
            }
        }
        out.write("\"")
    }

    override fun flush() = out.flush()
    override fun close() = out.close()
}

class StreamingJsonReader(private val reader: Reader) : Closeable {
    private var peeked: Int = -1
    private var hasPeeked = false

    private fun nextChar(): Int {
        if (hasPeeked) {
            hasPeeked = false
            return peeked
        }
        return reader.read()
    }

    private fun peekChar(): Int {
        if (!hasPeeked) {
            peeked = reader.read()
            hasPeeked = true
        }
        return peeked
    }

    private fun skipWhitespace(): Int {
        while (true) {
            val c = nextChar()
            if (c == -1 || !Character.isWhitespace(c)) return c
        }
    }

    fun beginArray() {
        val c = skipWhitespace()
        if (c != '['.code) throw IllegalStateException("Expected '[' but found '${c.toChar()}'")
    }

    fun endArray() {
        val c = skipWhitespace()
        if (c != ']'.code) throw IllegalStateException("Expected ']' but found '${c.toChar()}'")
    }

    fun beginObject() {
        val c = skipWhitespace()
        if (c != '{'.code) throw IllegalStateException("Expected '{' but found '${c.toChar()}'")
    }

    fun endObject() {
        val c = skipWhitespace()
        if (c != '}'.code) throw IllegalStateException("Expected '}' but found '${c.toChar()}'")
    }

    fun hasNext(): Boolean {
        while (true) {
            val c = peekChar()
            if (c == -1) return false
            if (Character.isWhitespace(c)) {
                nextChar()
                continue
            }
            if (c == ','.code) {
                nextChar()
                continue
            }
            return c != ']'.code && c != '}'.code
        }
    }

    fun nextName(): String {
        val str = nextStringOrNull() ?: throw IllegalStateException("Expected JSON field name")
        val colon = skipWhitespace()
        if (colon != ':'.code) throw IllegalStateException("Expected ':' after name '$str' but found '${colon.toChar()}'")
        return str
    }

    fun nextStringOrNull(): String? {
        val c = skipWhitespace()
        if (c == 'n'.code) {
            val u = nextChar()
            val l1 = nextChar()
            val l2 = nextChar()
            if (u == 'u'.code && l1 == 'l'.code && l2 == 'l'.code) return null
            throw IllegalStateException("Malformed JSON literal starting with 'n'")
        }
        if (c != '"'.code) {
            val sb = StringBuilder()
            sb.append(c.toChar())
            while (true) {
                val p = peekChar()
                if (p == -1 || p == ','.code || p == '}'.code || p == ']'.code || Character.isWhitespace(p)) break
                sb.append(nextChar().toChar())
            }
            return sb.toString()
        }
        val sb = StringBuilder()
        while (true) {
            val ch = nextChar()
            if (ch == -1) throw IllegalStateException("Unterminated string")
            if (ch == '"'.code) break
            if (ch == '\\'.code) {
                when (val esc = nextChar()) {
                    '\\'.code -> sb.append('\\')
                    '"'.code -> sb.append('"')
                    '/'.code -> sb.append('/')
                    'b'.code -> sb.append('\b')
                    'f'.code -> sb.append('\u000C')
                    'n'.code -> sb.append('\n')
                    'r'.code -> sb.append('\r')
                    't'.code -> sb.append('\t')
                    'u'.code -> {
                        val u1 = nextChar().toChar()
                        val u2 = nextChar().toChar()
                        val u3 = nextChar().toChar()
                        val u4 = nextChar().toChar()
                        val hex = String(charArrayOf(u1, u2, u3, u4)).toInt(16)
                        sb.append(hex.toChar())
                    }
                    else -> sb.append(esc.toChar())
                }
            } else {
                sb.append(ch.toChar())
            }
        }
        return sb.toString()
    }

    fun nextIntOrDefault(default: Int = 0): Int {
        val str = nextStringOrNull() ?: return default
        return str.toIntOrNull() ?: default
    }

    fun skipValue() {
        while (true) {
            val c = peekChar()
            if (c == -1) return
            if (Character.isWhitespace(c) || c == ','.code) {
                nextChar()
                continue
            }
            break
        }
        val c = nextChar()
        when (c) {
            '['.code -> {
                var depth = 1
                while (depth > 0) {
                    val ch = nextChar()
                    if (ch == -1) break
                    if (ch == '"'.code) skipQuotedString()
                    else if (ch == '['.code) depth++
                    else if (ch == ']'.code) depth--
                }
            }
            '{'.code -> {
                var depth = 1
                while (depth > 0) {
                    val ch = nextChar()
                    if (ch == -1) break
                    if (ch == '"'.code) skipQuotedString()
                    else if (ch == '{'.code) depth++
                    else if (ch == '}'.code) depth--
                }
            }
            '"'.code -> skipQuotedString()
            else -> {
                while (true) {
                    val p = peekChar()
                    if (p == -1 || p == ','.code || p == '}'.code || p == ']'.code || Character.isWhitespace(p)) break
                    nextChar()
                }
            }
        }
    }

    private fun skipQuotedString() {
        while (true) {
            val ch = nextChar()
            if (ch == -1 || ch == '"'.code) break
            if (ch == '\\'.code) nextChar()
        }
    }

    override fun close() = reader.close()
}
