package com.example.scan

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.scan.model.AggregatedCode
import com.example.scan.model.AggregatedCode_
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.objectbox.Box
import com.example.scan.model.ScannedCode
import com.example.scan.model.ScannedCode_
import com.example.scan.task.CheckResult
import io.objectbox.BoxStore

class BarcodeScannerProcessor(
    private val graphicOverlay: GraphicOverlay,
    private val context: Context,
    private val listener: OnBarcodeScannedListener,
    private val boxStore: BoxStore
) {

    private val scannedCodeBox: Box<ScannedCode> = boxStore.boxFor(ScannedCode::class.java)
    private val aggregatedCodeBox: Box<AggregatedCode> = boxStore.boxFor(AggregatedCode::class.java)
    private var lastSuccessfulScanTime: Long = 0
    private var invalidCodes = emptySet<String>()
    private val activeGraphics = mutableMapOf<String, BarcodeGraphic>()
    private val GRAPHIC_LIFETIME_MS = 300L

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_EAN_13)
        .build()

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)
    private val gs1Parser = GS1Parser()

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        if (scannedCodeBox.count() > 0) {
            Log.d("BarcodeScanner", "Clearing buffer due to inactivity.")
            scannedCodeBox.removeAll()
            invalidCodes = emptySet()
            listener.onBarcodeCountUpdated(0)
        }
    }
    fun processImageProxy(imageProxy: ImageProxy, currentTask: com.example.scan.model.Task?) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        inactivityHandler.removeCallbacks(inactivityRunnable)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSuccessfulScanTime > 100) {
                            lastSuccessfulScanTime = currentTime
                            handleBarcodes(barcodes, currentTask, imageProxy)
                        }
                        inactivityHandler.postDelayed(inactivityRunnable, 3000)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeScanner", "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    updateAndRedrawGraphics() // Always update graphics to handle removal
                    imageProxy.close()
                }
        }
    }
    private fun handleBarcodes(barcodes: List<Barcode>, currentTask: com.example.scan.model.Task?, imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val codesInFrame = mutableSetOf<String>()

        // Process all barcodes detected in the current frame
        for (barcode in barcodes) {
            val rawValue = barcode.rawValue ?: continue
            codesInFrame.add(rawValue)

            // If the graphic is already active, just update its timestamp
            if (activeGraphics.containsKey(rawValue)) {
                activeGraphics[rawValue]?.lastSeenTimestamp = currentTime
                continue
            }

            // New barcode detected, process and create a graphic for it
            val existingCode = scannedCodeBox.query(ScannedCode_.code.equal(rawValue)).build().findFirst()
            val isDuplicateInAggregation = aggregatedCodeBox.query(AggregatedCode_.fullCode.equal(rawValue)).build().findFirst() != null

            if (existingCode == null && !isDuplicateInAggregation) {
                val (contentType, gs1Data) = checkLogic(rawValue)
                val scannedCode = ScannedCode(
                    code = rawValue,
                    timestamp = currentTime,
                    codeType = getBarcodeFormatName(barcode.format),
                    contentType = contentType,
                    gs1Data = gs1Data
                )
                scannedCodeBox.put(scannedCode)
                Log.d("BarcodeScanner", "Scanned new code: $rawValue, Type: $contentType")
            }

            // Create a new graphic
            val isInvalid = invalidCodes.contains(rawValue)
            val newGraphic = BarcodeGraphic(graphicOverlay, barcode, isDuplicateInAggregation || isInvalid, currentTime)
            activeGraphics[rawValue] = newGraphic
        }

        // Trigger auto-focus for the first barcode in the frame
        if (barcodes.isNotEmpty()) {
            val firstBarcode = barcodes.first()
            val boundingBox = firstBarcode.boundingBox
            if (boundingBox != null) {
                val center = PointF(boundingBox.centerX().toFloat(), boundingBox.centerY().toFloat())
                listener.onFocusRequired(center, imageProxy.width, imageProxy.height)
            }
        }
    }

    private fun updateAndRedrawGraphics() {
        val currentTime = System.currentTimeMillis()

        // Remove old graphics that haven't been seen for a while
        val iterator = activeGraphics.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value.lastSeenTimestamp > GRAPHIC_LIFETIME_MS) {
                iterator.remove()
            }
        }

        // Redraw the overlay with active graphics
        graphicOverlay.clear()
        activeGraphics.values.forEach { graphic ->
            graphicOverlay.add(graphic)
        }

        // Task processing logic
        (context as? MainActivity)?.taskProcessor?.let { processor ->
            currentTask?.let { task ->
                val allCodes = scannedCodeBox.all
                val expectedCodeCount = (task.numPacksInBox ?: 0) + 1
                if (allCodes.size >= expectedCodeCount) {
                    when (val result = processor.check(allCodes, task)) {
                        is CheckResult.Success -> {
                            Log.d("BarcodeScanner", "Task check successful!")
                            listener.onCheckSucceeded()
                            invalidCodes = emptySet()
                            scannedCodeBox.removeAll()
                            allCodes.forEach { activeGraphics.remove(it.code) }
                        }
                        is CheckResult.Failure -> {
                            Log.w("BarcodeScanner", "Task check failed: ${result.reason}")
                            listener.onCheckFailed(result.reason)
                            invalidCodes = result.invalidCodes
                            scannedCodeBox.removeAll()
                        }
                    }
                }
            }
        }
        listener.onBarcodeCountUpdated(scannedCodeBox.count())
    }

    private fun checkLogic(code: String): Pair<String, MutableList<String>> {
        return when {
            code.startsWith("]C1") || code.contains('\u001d') -> {
                val parsedData = gs1Parser.parse(code)
                if (parsedData.isNotEmpty()) {
                    // Check if the content is exclusively an SSCC
                    if (parsedData.size == 1 && parsedData.containsKey("00")) {
                        Pair("GS1_SSCC", parsedData.map { "${it.key}:${it.value}" }.toMutableList())
                    } else {
                        val contentType = if (code.contains('\u001d')) "GS1_DATAMATRIX" else "GS1_CODE128"
                        Pair(contentType, parsedData.map { "${it.key}:${it.value}" }.toMutableList())
                    }
                } else {
                    Pair("GS1_ERROR", mutableListOf())
                }
            }
            code.length == 13 && code.all { it.isDigit() } -> { // EAN-13
                val parsedData = gs1Parser.parse(code)
                Pair("GS1_EAN13", parsedData.map { "${it.key}:${it.value}" }.toMutableList())
            }
            gs1Parser.isSSCC(code) -> Pair("GS1_SSCC", mutableListOf("00:$code"))
            code.startsWith("00") && code.length == 20 && code.all { it.isDigit() } -> Pair("GS1_SSCC", mutableListOf("00:${code.substring(2)}"))
            else -> Pair("TEXT", mutableListOf())
        }
    }

    private fun getBarcodeFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "Aztec"
            else -> "Unknown"
        }
    }

    interface OnBarcodeScannedListener {
        fun onFocusRequired(point: PointF, imageWidth: Int, imageHeight: Int)
        fun onBarcodeCountUpdated(totalCount: Long)
        fun onCheckSucceeded()
        fun onCheckFailed(reason: String)
    }

    fun close() {
        scanner.close()
    }
}
