package com.example.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BarcodeScannerProcessor(
    private val graphicOverlay: GraphicOverlay,
    context: Context
) {
    private val networkClient = NetworkClient(context)
    private val settingsManager = SettingsManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_CODE_128, // SSCC is a subset of Code 128
            Barcode.FORMAT_QR_CODE
        )
        .build()

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)
    private val executor: Executor = Executors.newSingleThreadExecutor()

    fun processImageProxy(image: androidx.camera.core.ImageProxy) {
        val inputImage = InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener(executor) { barcodes ->
                graphicOverlay.clear()
                val validCodes = mutableListOf<String>()
                for (barcode in barcodes) {
                    // Handle QR code for settings update
                    if (barcode.format == Barcode.FORMAT_QR_CODE && barcode.rawValue?.startsWith("http") == true) {
                        coroutineScope.launch {
                            settingsManager.loadSettingsFromQrCode(barcode.rawValue!!)
                        }
                        graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode, true)) // Always show QR as valid
                    } else {
                        val isValid = checkLogic(barcode)
                        graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode, isValid))
                        if (isValid) {
                            barcode.rawValue?.let { validCodes.add(it) }
                        }
                    }
                }
                if (validCodes.isNotEmpty()) {
                    coroutineScope.launch {
                        networkClient.sendData(validCodes)
                    }
                }
                graphicOverlay.postInvalidate()
            }
            .addOnFailureListener(executor) { e ->
                Log.e("BarcodeScanner", "Error processing image", e)
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    private fun checkLogic(barcode: Barcode): Boolean {
        // This is a placeholder for your logic check.
        return barcode.rawValue?.length ?: 0 > 5
    }

    private inner class BarcodeGraphic(
        overlay: GraphicOverlay,
        private val barcode: Barcode,
        private val isValid: Boolean
    ) : GraphicOverlay.Graphic(overlay) {

        private val boundingRectPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8.0f
        }

        override fun draw(canvas: Canvas) {
            val rect = calculateRect(barcode.boundingBox!!)
            boundingRectPaint.color = if (isValid) Color.GREEN else Color.GRAY
            canvas.drawRect(rect, boundingRectPaint)
        }
    }
}
