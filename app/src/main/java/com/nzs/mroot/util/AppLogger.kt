package com.nzs.mroot.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "MRoot"
    private const val LOG_FILE_NAME = "mroot.log"
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var isCrashHandlerInstalled = false

    @JvmStatic
    fun getLogFile(context: Context): File {
        val deContext = runCatching { context.createDeviceProtectedStorageContext() }.getOrDefault(context)
        val dir = File(deContext.filesDir, "logs").apply {
            if (!exists()) mkdirs()
        }
        return File(dir, LOG_FILE_NAME)
    }

    @JvmStatic
    @JvmOverloads
    fun append(context: Context, text: String, tag: String = TAG) {
        Log.i(tag, text.trimEnd())
        synchronized(lock) {
            try {
                val file = getLogFile(context)
                FileWriter(file, true).use { fw ->
                    fw.write(text)
                    fw.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file", e)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun appendLine(context: Context, line: String, tag: String = TAG) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        append(context, "[$timestamp] $line\n", tag)
    }

    @JvmStatic
    fun readLogs(context: Context): String {
        synchronized(lock) {
            return try {
                val file = getLogFile(context)
                if (file.exists() && file.length() > 0) {
                    file.readText()
                } else {
                    "No logs recorded yet."
                }
            } catch (e: Exception) {
                "Error reading logs: ${e.message}"
            }
        }
    }

    @JvmStatic
    fun clearLogs(context: Context): Boolean {
        synchronized(lock) {
            return try {
                val file = getLogFile(context)
                if (file.exists()) {
                    file.writeText("")
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    @JvmStatic
    fun getLogFileSizeFormatted(context: Context): String {
        synchronized(lock) {
            val file = getLogFile(context)
            if (!file.exists()) return "0 B"
            val bytes = file.length()
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            }
        }
    }

    @JvmStatic
    fun installCrashHandler(context: Context) {
        if (isCrashHandlerInstalled) return
        isCrashHandlerInstalled = true

        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = throwable.stackTraceToString()
                appendLine(
                    appContext,
                    "FATAL CRASH [Thread: ${thread.name}]: ${throwable.javaClass.name}: ${throwable.message}\n$stackTrace",
                    "MRootCrash"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in crash handler", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
