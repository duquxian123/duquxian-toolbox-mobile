package com.duquxian.toolbox.debug

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val TAG = "ToolboxDiagnostics"
    private const val MAX_FILE_BYTES = 256L * 1024L
    private const val MAX_READ_CHARS = 100_000
    private const val LOG_FILE_NAME = "toolbox-diagnostics.log"

    private val writeLock = Any()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null
    private var previousCrashHandler: Thread.UncaughtExceptionHandler? = null

    private val crashHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        runCatching {
            append(
                level = "ERROR",
                tag = "CRASH",
                message = "Uncaught exception on ${thread.name}\n" +
                    Log.getStackTraceString(throwable),
            )
        }
        previousCrashHandler?.uncaughtException(thread, throwable)
            ?: Runtime.getRuntime().halt(1)
    }

    @Synchronized
    fun initialize(context: Context) {
        if (logFile != null) return

        val appContext = context.applicationContext
        val directory = File(appContext.filesDir, "diagnostics")
        directory.mkdirs()
        val file = File(directory, LOG_FILE_NAME)
        if (file.length() > MAX_FILE_BYTES) {
            file.writeText("")
        }
        logFile = file

        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        event(
            tag = "APP",
            message = "Started ${appContext.packageName} ${packageInfo.versionName} " +
                "(${packageInfo.longVersionCode}); " +
                "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} " +
                "(API ${Build.VERSION.SDK_INT})",
        )

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler !== crashHandler) {
            previousCrashHandler = currentHandler
            Thread.setDefaultUncaughtExceptionHandler(crashHandler)
        }
    }

    fun event(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
        append("INFO", tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val detail = if (throwable == null) {
            message
        } else {
            "$message\n${Log.getStackTraceString(throwable)}"
        }
        Log.e(TAG, "[$tag] $detail", throwable)
        append("ERROR", tag, detail)
    }

    fun read(): String {
        val file = logFile ?: return ""
        val content = runCatching { file.readText() }.getOrDefault("")
        return content.takeLast(MAX_READ_CHARS)
    }

    fun readSupportSummary(maxLines: Int = 120): String {
        val lines = read().lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return ""
        val summary = lines.takeLast(maxLines).joinToString("\n")
        return "Diagnostic summary: ${minOf(maxLines, lines.size)} of ${lines.size} lines\n\n$summary"
    }

    fun clear() {
        val file = logFile ?: return
        runCatching { file.writeText("") }
        event("LOGS", "Diagnostic log cleared")
    }

    private fun append(level: String, tag: String, message: String) {
        val file = logFile ?: return
        synchronized(writeLock) {
            runCatching {
                if (file.length() > MAX_FILE_BYTES) {
                    file.writeText("")
                }
                val timestamp = timeFormat.format(Date())
                file.appendText("$timestamp $level [$tag] $message\n")
            }
        }
    }
}

