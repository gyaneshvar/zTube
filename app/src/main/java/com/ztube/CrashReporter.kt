package com.ztube

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.util.Date

object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_REPORT = 200_000
    private const val MAX_SHARE = 400_000
    private const val DEBUG_LOG_SEPARATOR = "\n--- Debug log ---\n"
    private const val PREVIOUS_SEPARATOR = "\n--- Previous session ---\n"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashFile = File(app.filesDir, FILE_NAME)
            var headLength = 0
            try {
                val head = header(app) + "Thread: ${thread.name}\n\n" + throwable.stackTraceToString().take(50_000)
                crashFile.writeText(head)
                headLength = head.length
            } catch (_: Throwable) {
                try {
                    crashFile.writeText(throwable.stackTraceToString().take(50_000))
                } catch (_: Throwable) {
                }
            }
            try {
                val room = (MAX_REPORT - headLength - DEBUG_LOG_SEPARATOR.length).coerceAtLeast(0)
                crashFile.appendText(DEBUG_LOG_SEPARATOR + DebugLog.snapshot().takeLast(room))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return try {
            if (file.exists()) (file.readText() + previousSection(context)).also { file.delete() } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun header(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return buildString {
            appendLine("Time: ${Date()}")
            appendLine("App: ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Memory: ${DebugLog.memory()}")
        }
    }

    fun debugReport(context: Context): String =
        (header(context) + DEBUG_LOG_SEPARATOR + DebugLog.snapshot() + previousSection(context)).takeLast(MAX_SHARE)

    private fun previousSection(context: Context): String =
        DebugLog.previous(context)?.let { PREVIOUS_SEPARATOR + it } ?: ""
}
