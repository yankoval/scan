package com.example.scan

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(
    private val context: Context,
    private val settingsManager: SettingsManager
) : Timber.DebugTree() {

    private val logFile: File by lazy {
        val documentsDir = context.getExternalFilesDir("Documents")
        val logDir = File(documentsDir, "scan")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        File(logDir, "debug.log")
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.DEBUG) {
            return
        }

        try {
            checkAndRotateLog()

            val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val level = when (priority) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> "?"
            }

            val appName = "scan"
            // Use the provided tag as the procedure/component
            val procedure = tag ?: "Unknown"

            // Format: Время, уровень, приложение, процедура, строка
            val formattedMessage = "$timeStamp, $level, $appName, $procedure, $message\n"

            logFile.appendText(formattedMessage)

        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Error writing to log file", e)
        }
    }

    private fun checkAndRotateLog() {
        val maxLogSize = settingsManager.getMaxLogSizeMb() * 1024 * 1024 // to Bytes
        if (logFile.exists() && logFile.length() > maxLogSize) {
            val backupFile = File(logFile.parent, "debug.log.1")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        }
    }
}
