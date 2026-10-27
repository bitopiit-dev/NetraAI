package com.factory.netra

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the last crash to a file so it can be shown on the next launch.
 * On a TV box there is no easy Logcat, so the app itself shows what went wrong.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                file(ctx).writeText(
                    "Netra AI ${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"})\n" +
                        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                        "${Build.SUPPORTED_ABIS.firstOrNull()}\n" +
                        "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} thread=${thread.name}\n\n" +
                        sw.toString().take(12_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Returns the saved crash report once, then deletes it. */
    fun takeLast(ctx: Context): String? {
        val f = file(ctx)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull().also { f.delete() }
    }
}
