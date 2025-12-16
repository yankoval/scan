package com.example.scan

import android.app.Application
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Plant a debug tree that logs to logcat.
        // The FileLoggingTree will be planted later in MainActivity
        // after permissions are granted.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
