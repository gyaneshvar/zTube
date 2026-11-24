package com.ztube

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.util.Date

object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(app.filesDir, FILE_NAME).writeText(report(app, thread, throwable))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return try {
            if (file.exists()) file.readText().also { file.delete() } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun report(context: Context, thread: Thread, throwable: Throwable): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return buildString {
            appendLine("Time: ${Date()}")
            appendLine("App: ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(throwable.stackTraceToString().take(50_000))
        }
    }
}
