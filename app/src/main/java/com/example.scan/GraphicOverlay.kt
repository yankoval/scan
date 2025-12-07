package com.example.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector
import kotlin.math.ceil

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var cameraSelector: Int = CameraSelector.LENS_FACING_BACK

    abstract class Graphic(private val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        protected fun calculateRect(boundingBox: Rect): RectF {
            val scaleX = overlay.width.toFloat() / overlay.imageWidth.toFloat()
            val scaleY = overlay.height.toFloat() / overlay.imageHeight.toFloat()
            val scale = scaleX.coerceAtLeast(scaleY)

            val offsetX = (overlay.width.toFloat() - ceil(overlay.imageWidth.toFloat() * scale)) / 2.0f
            val offsetY = (overlay.height.toFloat() - ceil(overlay.imageHeight.toFloat() * scale)) / 2.0f

            val mappedBoundingBox = RectF().apply {
                left = boundingBox.left * scale + offsetX
                top = boundingBox.top * scale + offsetY
                right = boundingBox.right * scale + offsetX
                bottom = boundingBox.bottom * scale + offsetY
            }
            return mappedBoundingBox
        }
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
    }

    fun setCameraInfo(width: Int, height: Int, facing: Int) {
        synchronized(lock) {
            imageWidth = width
            imageHeight = height
            cameraSelector = facing
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            for (graphic in graphics) {
                graphic.draw(canvas)
            }
        }
    }
}
