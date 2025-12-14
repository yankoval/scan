package com.example.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
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
    context: Context,
    private val focusListener: OnFocusRequiredListener
) {
    private val networkClient = NetworkClient(context)
    private val settingsManager = SettingsManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var isFocusTriggered = false

    interface OnFocusRequiredListener {
        fun onFocusRequired(point: PointF)
    }

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

                if (barcodes.isNotEmpty() && !isFocusTriggered) {
                    isFocusTriggered = true
                    val centerPoint = calculateAverageCenter(barcodes)
                    focusListener.onFocusRequired(centerPoint)
                } else if (barcodes.isEmpty()) {
                    isFocusTriggered = false
                }

                graphicOverlay.clear()
                val validCodes = mutableListOf<String>()
                for (barcode in barcodes) {
                    if (barcode.format == Barcode.FORMAT_QR_CODE && barcode.rawValue?.startsWith("http") == true) {
                        coroutineScope.launch {
                            settingsManager.loadSettingsFromQrCode(barcode.rawValue!!)
                        }
                        graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode, true))
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
                isFocusTriggered = false
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    private fun calculateAverageCenter(barcodes: List<Barcode>): PointF {
        var totalX = 0f
        var totalY = 0f
        barcodes.forEach { barcode ->
            val boundingBox = barcode.boundingBox!!
            totalX += boundingBox.centerX()
            totalY += boundingBox.centerY()
        }
        return PointF(totalX / barcodes.size, totalY / barcodes.size)
    }

    private fun checkLogic(barcode: Barcode): Boolean {
        return barcode.rawValue?.length ?: 0 > 5
    }

    private class BarcodeGraphic(
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
