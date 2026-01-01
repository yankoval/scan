package com.example.scan

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.barcode.common.Barcode

class BarcodeGraphic(overlay: GraphicOverlay, private val barcode: Barcode) : GraphicOverlay.Graphic(overlay) {

    private val rectPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4.0f
    }

    private val barcodePaint = Paint().apply {
        color = Color.WHITE
        textSize = 36.0f
    }

    override fun draw(canvas: Canvas) {
        barcode.boundingBox?.let { boundingBox ->
            val rect = calculateRect(boundingBox)
            canvas.drawRect(rect, rectPaint)
            canvas.drawText(barcode.rawValue ?: "", rect.left, rect.bottom, barcodePaint)
        }
    }
}
