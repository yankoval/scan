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
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var cameraSelector: Int = CameraSelector.LENS_FACING_BACK

    abstract inner class Graphic(private val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        protected fun calculateRect(boundingBox: Rect): RectF {
            // The camera image is in landscape mode, but the view is in portrait.
            // Therefore, we must swap the image dimensions to calculate the scale factor.
            val scaleX = overlay.width.toFloat() / overlay.imageHeight.toFloat()
            val scaleY = overlay.height.toFloat() / overlay.imageWidth.toFloat()
            val scale = min(scaleX, scaleY)

            // Calculate offsets to center the scaled image within the view.
            val offsetX = (overlay.width.toFloat() - overlay.imageHeight.toFloat() * scale) / 2.0f
            val offsetY = (overlay.height.toFloat() - overlay.imageWidth.toFloat() * scale) / 2.0f

            val mappedBoundingBox = RectF()

            // Map the coordinates by rotating 90 degrees and then scaling.
            mappedBoundingBox.left = boundingBox.top * scale + offsetX
            mappedBoundingBox.right = boundingBox.bottom * scale + offsetX

            // The Y coordinates need to be inverted because the camera's coordinate system
            // starts from the top-left, while the view's starts from the top-left.
            mappedBoundingBox.top = overlay.height - (boundingBox.right * scale + offsetY)
            mappedBoundingBox.bottom = overlay.height - (boundingBox.left * scale + offsetY)

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
