package com.example.scan

import android.app.Application
import com.example.scan.model.MyObjectBox
import io.objectbox.BoxStore
import timber.log.Timber

class MainApplication : Application() {
    lateinit var boxStore: BoxStore
        private set

    override fun onCreate() {
        super.onCreate()
        boxStore = MyObjectBox.builder().androidContext(this).build()
        // Always plant trees to get around BuildConfig issues in CI
        Timber.plant(Timber.DebugTree(), FileLoggingTree(this))
    }
}
