package com.example.scan

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.barcode.common.Barcode

class BarcodeGraphic(
    overlay: GraphicOverlay,
    private val barcode: Barcode,
    var isDuplicate: Boolean = false
) : GraphicOverlay.Graphic(overlay) {

    private val validPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
        alpha = 200
    }

    private val invalidPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
        alpha = 200
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40.0f
        alpha = 200
    }

    override fun draw(canvas: Canvas) {
        barcode.boundingBox?.let { boundingBox ->
            // Adjusts the bounding box to match the view scale and orientation.
            val rect = calculateRect(boundingBox)
            val paint = if (isDuplicate) invalidPaint else validPaint
            canvas.drawRect(rect, paint)

            // Draws the barcode raw value below the box.
            barcode.rawValue?.let {
                canvas.drawText(it, rect.left, rect.bottom + 40f, textPaint)
            }
        }
    }
}
