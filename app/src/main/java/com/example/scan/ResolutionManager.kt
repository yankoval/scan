package com.example.scan

import android.util.Log
import android.util.Size
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs

class ResolutionManager(
    private var supportedResolutions: List<Size>,
    private val onResolutionSelected: (Size) -> Unit
) {
    private var currentIndex = 0
    private var timer: Timer? = null
    private var lastBarcodeCount = 0
    private var stableBarcodeCount = 0
    private var scanTunePeriod: Long = 3000L // Default value, will be updated from settings

    init {
        // Filter for 16:9 resolutions and sort them from highest to lowest
        this.supportedResolutions = supportedResolutions
            .filter { is16to9(it) }
            .sortedByDescending { it.width.toLong() * it.height }
            .distinct() // Remove duplicates

        if (this.supportedResolutions.isEmpty()) {
            Log.e(TAG, "No 16:9 resolutions found, using default list.")
            // Fallback to a default list if no supported 16:9 resolutions are found
            this.supportedResolutions = listOf(
                Size(1920, 1080),
                Size(1280, 720)
            )
        }

        // Select the top 5 resolutions if more are available
        this.supportedResolutions = this.supportedResolutions.take(5)

        Log.d(TAG, "Initialized with resolutions: ${this.supportedResolutions}")
        selectInitialResolution()
    }

    private fun is16to9(size: Size): Boolean {
        val ratio = size.width.toFloat() / size.height.toFloat()
        return abs(ratio - 16.0f / 9.0f) < 0.01
    }

    private fun selectInitialResolution() {
        currentIndex = 0
        val initialResolution = supportedResolutions.getOrNull(currentIndex)
        if (initialResolution != null) {
            Log.d(TAG, "Selecting initial resolution: $initialResolution")
            onResolutionSelected(initialResolution)
        } else {
            Log.e(TAG, "Could not select an initial resolution.")
        }
    }

    fun start(tunePeriod: Long) {
        this.scanTunePeriod = tunePeriod * 1000L // convert seconds to ms
        stop() // Ensure no existing timer is running
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                analyzeAndAdjustResolution()
            }
        }, scanTunePeriod, scanTunePeriod)
        Log.d(TAG, "Resolution tuning timer started with period: $scanTunePeriod ms")
    }

    fun stop() {
        timer?.cancel()
        timer = null
        Log.d(TAG, "Resolution tuning timer stopped.")
    }

    fun onBarcodeCountUpdate(count: Int) {
        lastBarcodeCount = count
    }

    private fun analyzeAndAdjustResolution() {
        Log.d(TAG, "Analyzing: last count: $lastBarcodeCount, stable count: $stableBarcodeCount")

        if (lastBarcodeCount > stableBarcodeCount) {
            // New max codes found, this is the new baseline
            stableBarcodeCount = lastBarcodeCount
            Log.d(TAG, "New stable code count: $stableBarcodeCount. Staying at current resolution.")
        } else if (lastBarcodeCount < stableBarcodeCount) {
            // We lost codes, need to increase resolution
            increaseResolution()
            stableBarcodeCount = lastBarcodeCount // Reset baseline
        } else if (lastBarcodeCount == stableBarcodeCount && lastBarcodeCount > 0) {
            // Stable and successful scan, try to decrease resolution for performance
            decreaseResolution()
        }
        // If lastBarcodeCount is 0 and stableBarcodeCount is 0, do nothing.
    }

    private fun increaseResolution() {
        if (currentIndex > 0) {
            currentIndex--
            val newResolution = supportedResolutions[currentIndex]
            Log.i(TAG, "Code count decreased. Increasing resolution to: $newResolution")
            onResolutionSelected(newResolution)
        } else {
            Log.d(TAG, "Already at the highest resolution.")
        }
    }

    private fun decreaseResolution() {
        if (currentIndex < supportedResolutions.size - 1) {
            currentIndex++
            val newResolution = supportedResolutions[currentIndex]
            Log.i(TAG, "Scan is stable. Decreasing resolution to: $newResolution")
            onResolutionSelected(newResolution)
        } else {
            Log.d(TAG, "Already at the lowest resolution.")
        }
    }

    companion object {
        private const val TAG = "ResolutionManager"
    }
}
