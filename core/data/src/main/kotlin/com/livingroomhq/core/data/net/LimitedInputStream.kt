package com.livingroomhq.core.data.net

import java.io.IOException
import java.io.InputStream

/** InputStream wrapper that rejects reads past [maxBytes]. */
class LimitedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
) : InputStream() {

    private var bytesRead = 0L

    override fun read(): Int {
        if (bytesRead >= maxBytes) throw sizeExceeded()
        val b = delegate.read()
        if (b >= 0) bytesRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bytesRead >= maxBytes) throw sizeExceeded()
        val allowed = (maxBytes - bytesRead).toInt().coerceAtMost(len)
        val n = delegate.read(b, off, allowed)
        if (n > 0) bytesRead += n
        return n
    }

    override fun close() = delegate.close()

    private fun sizeExceeded(): IOException =
        IOException("Response exceeds ${maxBytes / (1024 * 1024)}MB size limit")
}
