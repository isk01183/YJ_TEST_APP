package com.example.chargingapp

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReportStore {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildString {
                    appendLine("=== YJ_TEST_APP CRASH REPORT ===")
                    appendLine("time=" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
                    appendLine("thread=" + thread.name)
                    appendLine("manufacturer=" + Build.MANUFACTURER)
                    appendLine("model=" + Build.MODEL)
                    appendLine("device=" + Build.DEVICE)
                    appendLine("android=" + Build.VERSION.RELEASE)
                    appendLine("sdk=" + Build.VERSION.SDK_INT)
                    appendLine()
                    appendLine(throwable.stackTraceToString())
                }
                File(appContext.filesDir, FILE_NAME).writeText(report)
            } catch (_: Throwable) {
            }

            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
