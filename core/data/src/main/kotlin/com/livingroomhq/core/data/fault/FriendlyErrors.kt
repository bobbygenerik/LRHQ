package com.livingroomhq.core.data.fault

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns raw exception text (e.g. `Unable to resolve host "x": No address associated
 * with hostname`) into short, user-facing sentences. Raw detail stays in FaultLog.
 */
object FriendlyErrors {

    fun from(t: Throwable): String = when (t) {
        is UnknownHostException -> CANT_REACH
        is SocketTimeoutException -> TIMEOUT
        is ConnectException -> CANT_CONNECT
        is SSLException -> SECURE
        else -> from(t.message)
    }

    fun from(message: String?): String {
        val m = message.orEmpty().lowercase()
        return when {
            "unable to resolve host" in m || "no address associated" in m -> CANT_REACH
            "timeout" in m || "timed out" in m -> TIMEOUT
            "failed to connect" in m || "connection refused" in m -> CANT_CONNECT
            "ssl" in m || "certificate" in m || "handshake" in m -> SECURE
            Regex("""\b40[13]\b""").containsMatchIn(m) -> REFUSED
            Regex("""\b404\b""").containsMatchIn(m) -> NOT_FOUND
            Regex("""\b5\d\d\b""").containsMatchIn(m) -> SERVER
            "unterminated" in m || "parse" in m || "malformed" in m || "xml" in m -> BAD_DATA
            else -> GENERIC
        }
    }

    private const val CANT_REACH = "Couldn't reach the server. Check the link or your connection."
    private const val TIMEOUT = "The server didn't respond in time. Try again."
    private const val CANT_CONNECT = "Couldn't connect to the server."
    private const val SECURE = "Secure connection to the server failed."
    private const val REFUSED = "The server refused access. Check your username and password."
    private const val NOT_FOUND = "That link wasn't found on the server."
    private const val SERVER = "The server had a problem. Try again later."
    private const val BAD_DATA = "The server sent data that couldn't be read."
    private const val GENERIC = "Couldn't load that link. Check it and try again."

    /** Short form for one-line status summaries. */
    fun short(message: String?): String = when (from(message)) {
        CANT_REACH -> "can't reach server"
        TIMEOUT -> "server not responding"
        CANT_CONNECT -> "can't connect"
        SECURE -> "secure connection failed"
        REFUSED -> "access refused"
        NOT_FOUND -> "link not found"
        SERVER -> "server error"
        BAD_DATA -> "unreadable data"
        else -> "couldn't load"
    }
}
