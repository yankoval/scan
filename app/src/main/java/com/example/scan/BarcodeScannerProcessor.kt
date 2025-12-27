package com.example.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.example.scan.model.ScannedCode
import com.example.scan.model.ScannedCode_
import io.objectbox.Box
import io.objectbox.BoxStore
import se.t_mattsson.gs1.GS1Exception
import se.t_mattsson.gs1.elementstring.ElementStrings
import kotlinx.coroutines.CoroutineScope
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BarcodeScannerProcessor(
    private val graphicOverlay: GraphicOverlay,
    context: Context,
    private val listener: OnBarcodeScannedListener,
    boxStore: BoxStore
) {
    private val scannedCodeBox: Box<ScannedCode> = boxStore.boxFor(ScannedCode::class.java)
    private val settingsManager = SettingsManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var isFocusTriggered = false

    interface OnBarcodeScannedListener {
        fun onFocusRequired(point: PointF, imageWidth: Int, imageHeight: Int)
        fun onBarcodeCountUpdated(totalCount: Long)
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
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        val rotationDegrees = image.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener(executor) { barcodes ->
                listener.onBarcodeCountUpdated(scannedCodeBox.count())

                graphicOverlay.setCameraInfo(image.width, image.height, CameraSelector.LENS_FACING_BACK)

                val barcodesWithBounds = barcodes.filter { it.boundingBox != null }

                if (barcodesWithBounds.isNotEmpty() && !isFocusTriggered) {
                    isFocusTriggered = true
                    val centerPoint = calculateAverageCenter(barcodesWithBounds)
                    listener.onFocusRequired(centerPoint, image.width, image.height)
                } else if (barcodes.isEmpty()) {
                    isFocusTriggered = false
                }

                graphicOverlay.clear()
                for (barcode in barcodes) {
                    if (barcode.format == Barcode.FORMAT_QR_CODE && barcode.rawValue?.startsWith("http") == true) {
                        coroutineScope.launch {
                            settingsManager.loadSettingsFromQrCode(barcode.rawValue!!)
                        }
                        graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode, true))
                    } else {
                        val isValid = checkLogic(barcode)
                        graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode, isValid))
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
        val code = barcode.rawValue ?: return false

        if (scannedCodeBox.query(ScannedCode_.code.equal(code)).build().findFirst() != null) {
            return false
        }

        val codeType = when (barcode.format) {
            Barcode.FORMAT_DATA_MATRIX -> "DataMatrix"
            Barcode.FORMAT_QR_CODE -> "QRCode"
            Barcode.FORMAT_CODE_128 -> "Code128"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            else -> "Unknown"
        }

        var contentType: String
        val gs1Data = mutableListOf<String>()
        var isValid = true

        try {
            val result = ElementStrings.parse(code)
            if (result.isPartial) {
                // The library indicates a parsing error (e.g., malformed data)
                throw GS1Exception("Partial parse: " + result.errorMessage)
            }
            contentType = "GS1"
            result.elements.forEach { element ->
                gs1Data.add("${element.ai}:${element.data}")
            }
        } catch (e: GS1Exception) {
            // GS1 parsing failed.
            Log.w("BarcodeScanner", "GS1 parsing failed for code '$code': ${e.message}")

            // Now, check if it's a standard URL as a fallback.
            try {
                URL(code)
                contentType = "URL"
                // It's a valid URL, so it's a valid scan, just not GS1.
            } catch (urlException: Exception) {
                // If it's not a valid URL either, we mark it as a generic text code.
                // In this case, we consider the scan invalid because the initial parsing failed.
                contentType = "TEXT_ERROR"
                isValid = false
            }
        }

        scannedCodeBox.put(
            ScannedCode(
                code = code,
                codeType = codeType,
                contentType = contentType,
                gs1Data = gs1Data
            )
        )
        return isValid
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
            barcode.boundingBox?.let {
                val rect = calculateRect(it)
                boundingRectPaint.color = if (isValid) Color.GREEN else Color.GRAY
                canvas.drawRect(rect, boundingRectPaint)
            }
        }
    }
}
