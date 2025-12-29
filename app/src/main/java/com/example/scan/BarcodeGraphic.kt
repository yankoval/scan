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
        val rect = calculateRect(
            imageHeight = imageRect.height().toFloat(),
            imageWidth = imageRect.width().toFloat(),
            surfaceHeight = canvas.height.toFloat(),
            surfaceWidth = canvas.width.toFloat(),
            barcode.boundingBox!!
        )
        canvas.drawRect(rect, rectPaint)
        canvas.drawText(barcode.rawValue ?: "", rect.left, rect.bottom, barcodePaint)
    }

    private fun calculateRect(imageHeight: Float, imageWidth: Float, surfaceHeight: Float, surfaceWidth: Float, boundingBox: android.graphics.Rect): RectF {
        val scaleX = surfaceWidth / imageWidth
        val scaleY = surfaceHeight / imageHeight
        val scale = scaleX.coerceAtLeast(scaleY)

        val offsetX = (surfaceWidth - imageWidth * scale) / 2.0f
        val offsetY = (surfaceHeight - imageHeight * scale) / 2.0f

        return RectF(
            boundingBox.left * scale + offsetX,
            boundingBox.top * scale + offsetY,
            boundingBox.right * scale + offsetX,
            boundingBox.bottom * scale + offsetY
        )
    }
}
