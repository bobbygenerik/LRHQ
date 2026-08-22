package com.livingroomhq.crash

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Provider-free crash trail for a TV launcher that usually runs without an
 * attached debugger: the most recent uncaught exception is persisted to
 * filesDir/crash/last-crash.txt, survives restarts, and can be retrieved with
 * `adb shell run-as com.livingroomhq cat files/crash/last-crash.txt`.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the last recorded crash, if any (for diagnostics surfaces). */
    fun lastCrash(context: Context): String? =
        File(crashDir(context), FILE_NAME).takeIf(File::exists)?.readText()

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter()
            .also { throwable.printStackTrace(PrintWriter(it)) }
            .toString()
        val dir = crashDir(context)
        dir.mkdirs()
        File(dir, FILE_NAME).writeText(
            buildString {
                appendLine("time: ${System.currentTimeMillis()}")
                appendLine("thread: ${thread.name}")
                appendLine("version: 0.1.1")
                append(stack)
            },
        )
    }

    private fun crashDir(context: Context): File = File(context.filesDir, "crash")
}
