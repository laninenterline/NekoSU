package com.nekosu.data.logging

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions into a dedicated local file so a crash can be
 * exported from Settings -> Tools without adb/logcat access.
 */
object CrashLogger {
    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 128 * 1024L

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun read(context: Context): String = runCatching {
        val f = file(context)
        if (f.isFile) f.readText() else ""
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val f = file(context)
        if (f.isFile && f.length() > MAX_BYTES) {
            f.writeText(f.readText().takeLast((MAX_BYTES / 2).toInt()))
        }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("=== crash $ts ===")
        sb.appendLine("thread: ${thread.name}")
        sb.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE}")
        sb.appendLine(throwable.stackTraceToString())
        sb.appendLine()
        f.appendText(sb.toString())
    }
}
