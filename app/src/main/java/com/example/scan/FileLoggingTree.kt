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

    private val logFile: File? by lazy {
        val documentsDir = context.getExternalFilesDir("Documents")
        if (documentsDir == null) {
            Log.e("FileLoggingTree", "External storage not available. File logging is disabled.")
            null
        } else {
            val logDir = File(documentsDir, "scan")
            File(logDir, "debug.log")
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.DEBUG) {
            return
        }

        val currentLogFile = logFile ?: return

        try {
            val logDir = currentLogFile.parentFile
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs()
            }

            checkAndRotateLog(currentLogFile)

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
            val procedure = tag ?: "Unknown"

            val formattedMessage = "$timeStamp, $level, $appName, $procedure, $message\n"

            if (currentLogFile.parentFile?.exists() == true) {
                currentLogFile.appendText(formattedMessage)
            }

        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Error writing to log file", e)
        }
    }

    private fun checkAndRotateLog(file: File) {
        if (!file.exists()) {
            return
        }
        val maxLogSize = settingsManager.getMaxLogSizeMb() * 1024 * 1024 // to Bytes
        if (file.length() > maxLogSize) {
            val backupFile = File(file.parent, "debug.log.1")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            file.renameTo(backupFile)
        }
    }
}
