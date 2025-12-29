package com.example.scan

import android.content.Context
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.objectbox.Box
import com.example.scan.model.ScannedCode
import com.example.scan.model.ScannedCode_
import io.objectbox.BoxStore

class BarcodeScannerProcessor(
    private val graphicOverlay: GraphicOverlay,
    private val context: Context,
    private val listener: OnBarcodeScannedListener,
    private val boxStore: BoxStore,
    private val mainActivity: MainActivity
) {

    private val scannedCodeBox: Box<ScannedCode> = boxStore.boxFor(ScannedCode::class.java)
    private var lastSuccessfulScanTime: Long = 0

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_EAN_13)
        .build()

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)
    private val gs1Parser = GS1Parser()

    fun processImageProxy(imageProxy: ImageProxy, currentTask: com.example.scan.model.Task?) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSuccessfulScanTime > 100) {
                            lastSuccessfulScanTime = currentTime
                            handleBarcodes(barcodes, currentTask)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeScanner", "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
    private fun handleBarcodes(barcodes: List<Barcode>, currentTask: com.example.scan.model.Task?) {
        graphicOverlay.clear()
        for (barcode in barcodes) {
            val rawValue = barcode.rawValue
            if (rawValue != null) {
                val existingCode = scannedCodeBox.query(ScannedCode_.code.equal(rawValue)).build().findFirst()
                if (existingCode == null) {
                    val (contentType, gs1Data) = checkLogic(rawValue)
                    val scannedCode = ScannedCode(
                        code = rawValue,
                        timestamp = System.currentTimeMillis(),
                        contentType = contentType,
                        gs1Data = gs1Data
                    )
                    scannedCodeBox.put(scannedCode)
                    Log.d("BarcodeScanner", "Scanned code: $rawValue, Type: $contentType")
                }
            }
            graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode))
        }

        mainActivity.taskProcessor?.let { processor ->
            currentTask?.let { task ->
                val allCodes = scannedCodeBox.all
                if (processor.check(allCodes, task)) {
                    Log.d("BarcodeScanner", "Task check successful!")
                    mainActivity.updateAggregateCount()
                }
            }
        }

        val totalCount = scannedCodeBox.count()
        listener.onBarcodeCountUpdated(totalCount)

        if (barcodes.isNotEmpty()) {
            val firstBarcode = barcodes.first()
            val imageWidth = graphicOverlay.width
            val imageHeight = graphicOverlay.height
            val boundingBox = firstBarcode.boundingBox
            if (boundingBox != null) {
                val center = PointF(boundingBox.centerX().toFloat(), boundingBox.centerY().toFloat())
                listener.onFocusRequired(center, imageWidth, imageHeight)
            }
        }
    }

    private fun checkLogic(code: String): Pair<String, MutableList<String>> {
        return when {
            code.startsWith("]C1") || code.contains("\\u001d") -> {
                val parsedData = gs1Parser.parse(code)
                if (parsedData.isNotEmpty()) {
                    Pair("GS1_DATAMATRIX", parsedData.map { "${it.key}:${it.value}" }.toMutableList())
                } else {
                    Pair("GS1_ERROR", mutableListOf())
                }
            }
            code.length == 13 && code.all { it.isDigit() } -> { // EAN-13
                val parsedData = gs1Parser.parse(code)
                Pair("GS1_EAN13", parsedData.map { "${it.key}:${it.value}" }.toMutableList())
            }
            gs1Parser.isSSCC(code) -> Pair("GS1_SSCC", mutableListOf("00:${code}"))
            else -> Pair("TEXT", mutableListOf())
        }
    }

    interface OnBarcodeScannedListener {
        fun onFocusRequired(point: PointF, imageWidth: Int, imageHeight: Int)
        fun onBarcodeCountUpdated(totalCount: Long)
    }
}
