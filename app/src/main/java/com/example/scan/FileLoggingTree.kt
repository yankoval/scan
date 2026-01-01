package com.example.scan

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(private val context: Context) : Timber.Tree() {

    private val logFile: File by lazy {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        File(logDir, "app.log")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.VERBOSE || priority == Log.DEBUG) {
            return
        }

        try {
            val logTimeStamp = dateFormat.format(Date())
            val priorityChar = when (priority) {
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }

            val logMessage = "$logTimeStamp $priorityChar/$tag: $message\n"

            FileWriter(logFile, true).use {
                it.append(logMessage)
                t?.let { throwable ->
                    it.append(Log.getStackTraceString(throwable))
                    it.append("\n")
                }
            }
        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Error writing to log file", e)
        }
    }
}
