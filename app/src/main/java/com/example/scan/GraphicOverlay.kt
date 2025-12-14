package com.example.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
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
    private var rotationDegrees: Int = 0
    private var cameraSelector: Int = CameraSelector.LENS_FACING_BACK

    abstract class Graphic(private val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        protected fun calculateRect(boundingBox: Rect): RectF {
            val translatedPoint = overlay.translatePoint(
                PointF(boundingBox.left.toFloat(), boundingBox.top.toFloat()),
                overlay.imageWidth,
                overlay.imageHeight,
                overlay.rotationDegrees
            )
            val translatedRight = overlay.translatePoint(
                PointF(boundingBox.right.toFloat(), boundingBox.top.toFloat()),
                overlay.imageWidth,
                overlay.imageHeight,
                overlay.rotationDegrees
            ).x
            val translatedBottom = overlay.translatePoint(
                PointF(boundingBox.left.toFloat(), boundingBox.bottom.toFloat()),
                overlay.imageWidth,
                overlay.imageHeight,
                overlay.rotationDegrees
            ).y

            return RectF(translatedPoint.x, translatedPoint.y, translatedRight, translatedBottom)
        }
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun translatePoint(point: PointF, imageWidth: Int, imageHeight: Int, rotation: Int): PointF {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val imageWidthF = imageWidth.toFloat()
        val imageHeightF = imageHeight.toFloat()

        // Determine if the image is rotated by 90 or 270 degrees.
        val isrotated = rotation % 180 != 0
        val rotatedWidth = if (isrotated) imageHeightF else imageWidthF
        val rotatedHeight = if (isrotated) imageWidthF else imageHeightF

        val scaleX = viewWidth / rotatedWidth
        val scaleY = viewHeight / rotatedHeight
        val scale = min(scaleX, scaleY)

        val offsetX = (viewWidth - rotatedWidth * scale) / 2.0f
        val offsetY = (viewHeight - rotatedHeight * scale) / 2.0f

        val transformedX: Float
        val transformedY: Float

        when (rotation) {
            0 -> {
                transformedX = point.x * scale + offsetX
                transformedY = point.y * scale + offsetY
            }
            90 -> {
                transformedX = (imageHeightF - point.y) * scale + offsetX
                transformedY = point.x * scale + offsetY
            }
            180 -> {
                transformedX = (imageWidthF - point.x) * scale + offsetX
                transformedY = (imageHeightF - point.y) * scale + offsetY
            }
            270 -> {
                transformedX = point.y * scale + offsetX
                transformedY = (imageWidthF - point.x) * scale + offsetY
            }
            else -> throw IllegalArgumentException("Invalid rotation degree: $rotation")
        }

        return PointF(transformedX, transformedY)
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

    fun setRotationInfo(rotation: Int) {
        synchronized(lock) {
            rotationDegrees = rotation
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
