package com.example.scan

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.scan.model.AggregatePackage
import com.example.scan.model.AggregatePackage_
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
    private val aggregatePackageBox: Box<AggregatePackage> = boxStore.boxFor(AggregatePackage::class.java)
    private var invalidCodes = emptySet<String>()
    private val activeGraphics = mutableMapOf<String, BarcodeGraphic>()
    private val GRAPHIC_LIFETIME_MS = 300L

    private var lastBufferCodes: Set<String> = emptySet()
    private var lastBufferChangeTime: Long = 0
    private var isCheckTriggered: Boolean = false
    private val settingsManager = SettingsManager(context)

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
                        handleBarcodes(barcodes, currentTask, imageProxy)
                        inactivityHandler.postDelayed(inactivityRunnable, 3000)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeScanner", "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    updateAndRedrawGraphics(currentTask) // Always update graphics to handle removal
                    imageProxy.close()
                }
        }
    }
    private fun handleBarcodes(barcodes: List<Barcode>, currentTask: com.example.scan.model.Task?, imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Process all barcodes detected in the current frame
        for (barcode in barcodes) {
            val rawValue = barcode.rawValue ?: continue

            val (contentType, gs1DataList) = checkLogic(rawValue)

            val isDuplicateInAggregation = when (contentType) {
                "GS1_SSCC" -> aggregatePackageBox.query(AggregatePackage_.sscc.equal(rawValue)).build().findFirst() != null
                else -> aggregatedCodeBox.query(AggregatedCode_.fullCode.equal(rawValue)).build().findFirst() != null
            }

            // Determine if the code matches the current task
            var isMismatched = false
            if (currentTask != null) {
                val gs1DataMap = gs1DataList.associate {
                    val parts = it.split(":", limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                }
                val isProductMatch = isHonestSign(barcode, gs1DataMap, currentTask)
                val isSscc = contentType == "GS1_SSCC"
                if (!isProductMatch && !isSscc) {
                    isMismatched = true
                }
            }

            val isInvalid = invalidCodes.contains(rawValue)
            val isDuplicate = isDuplicateInAggregation || isInvalid

            // Update or create graphic for the barcode
            val existingGraphic = activeGraphics[rawValue]
            if (existingGraphic != null) {
                existingGraphic.barcode = barcode
                existingGraphic.isDuplicate = isDuplicate
                existingGraphic.isMismatched = isMismatched
                existingGraphic.lastSeenTimestamp = currentTime
            } else {
                val newGraphic = BarcodeGraphic(
                    overlay = graphicOverlay,
                    barcode = barcode,
                    isDuplicate = isDuplicate,
                    isMismatched = isMismatched,
                    lastSeenTimestamp = currentTime
                )
                activeGraphics[rawValue] = newGraphic
            }

            // Only add to buffer if it's a new valid code
            val existingCode = scannedCodeBox.query(ScannedCode_.code.equal(rawValue)).build().findFirst()
            if (existingCode == null && !isDuplicateInAggregation && !isMismatched) {
                val scannedCode = ScannedCode(
                    code = rawValue,
                    timestamp = currentTime,
                    codeType = getBarcodeFormatName(barcode.format),
                    contentType = contentType,
                    gs1Data = gs1DataList.toMutableList()
                )
                scannedCodeBox.put(scannedCode)
                Log.d("BarcodeScanner", "Scanned new code: $rawValue, Type: $contentType")
            }
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

    private fun updateAndRedrawGraphics(currentTask: com.example.scan.model.Task?) {
        val currentTime = System.currentTimeMillis()
        val coolingPeriodMs = settingsManager.getCoolingPeriodMs()

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
                val currentCodes = allCodes.map { it.code }.toSet()

                if (currentCodes != lastBufferCodes) {
                    lastBufferCodes = currentCodes
                    lastBufferChangeTime = currentTime
                    isCheckTriggered = false
                } else if (!isCheckTriggered && currentCodes.isNotEmpty()) {
                    val expectedCodeCount = (task.numPacksInBox ?: 0) + 1
                    if (currentCodes.size >= expectedCodeCount && currentTime - lastBufferChangeTime >= coolingPeriodMs) {
                        isCheckTriggered = true
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
                                if (allCodes.size >= expectedCodeCount) {
                                    scannedCodeBox.removeAll()
                                }
                            }
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

    private fun isHonestSign(barcode: Barcode, gs1DataMap: Map<String, String>, task: com.example.scan.model.Task?): Boolean {
        if (barcode.format != Barcode.FORMAT_DATA_MATRIX) return false
        val rawValue = barcode.rawValue ?: return false

        // Rule: Must start with GS (\u001d) or ]C1 prefix
        if (!rawValue.startsWith("\u001d") && !rawValue.startsWith("]C1")) return false

        // Rule: Must have Serial Number (21) and Crypto Tail (93)
        if (!gs1DataMap.containsKey("21") || !gs1DataMap.containsKey("93")) return false

        // Rule: Must have GTIN (01)
        val gtin = gs1DataMap["01"] ?: return false

        // Rule: If task is present, GTIN must match
        if (task != null && gtin != task.gtin) return false

        return true
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
