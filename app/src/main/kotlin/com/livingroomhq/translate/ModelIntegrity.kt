package com.livingroomhq.translate

import java.io.File
import java.security.MessageDigest

internal object ModelIntegrity {
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(64 * 1024).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyOrThrow(bytes: ByteArray, expectedHex: String, label: String) {
        val actual = sha256(bytes)
        if (!actual.equals(expectedHex, ignoreCase = true)) {
            error("$label SHA-256 mismatch expected=$expectedHex actual=$actual")
        }
    }

    fun verifyFileOrThrow(file: File, expectedHex: String, label: String) {
        val actual = sha256File(file)
        if (!actual.equals(expectedHex, ignoreCase = true)) {
            error("$label SHA-256 mismatch expected=$expectedHex actual=$actual")
        }
    }
}
