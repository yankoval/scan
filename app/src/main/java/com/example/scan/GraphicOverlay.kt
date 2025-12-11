package com.example.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector
import kotlin.math.min

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()
    internal var imageWidth: Int = 0
    internal var imageHeight: Int = 0
    internal var cameraSelector: Int = CameraSelector.LENS_FACING_BACK

    abstract class Graphic(private val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        protected fun calculateRect(boundingBox: Rect): RectF {
            val scaleX = overlay.width.toFloat() / overlay.imageWidth.toFloat()
            val scaleY = overlay.height.toFloat() / overlay.imageHeight.toFloat()

            // This is the key change: offsets are calculated but not used for scaling
            val offsetX = (overlay.width.toFloat() - overlay.imageWidth.toFloat() * scaleX) 
            val offsetY = (overlay.height.toFloat() - overlay.imageHeight.toFloat() * scaleY)

            val mappedBoundingBox = RectF()

            mappedBoundingBox.left = boundingBox.left * scaleX 
            mappedBoundingBox.right = boundingBox.right * scaleX
            mappedBoundingBox.top = boundingBox.top * scaleY
            mappedBoundingBox.bottom = boundingBox.bottom * scaleY

            // If using the front camera, the image is mirrored horizontally.
            if (overlay.cameraSelector == CameraSelector.LENS_FACING_FRONT) {
                val newLeft = overlay.width - mappedBoundingBox.right
                val newRight = overlay.width - mappedBoundingBox.left
                mappedBoundingBox.left = newLeft
                mappedBoundingBox.right = newRight
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
