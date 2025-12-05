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

        fun calculateRect(boundingBox: Rect): RectF {
            val scaleX = overlay.width.toFloat() / overlay.imageWidth.toFloat()
            val scaleY = overlay.height.toFloat() / overlay.imageHeight.toFloat()
            val scale = scaleX.coerceAtLeast(scaleY)
            val L = overlay.width.toFloat()
            val B = overlay.height.toFloat()
            val scaledWidth = ceil(overlay.imageWidth.toFloat() * scale)
            val scaledHeight = ceil(overlay.imageHeight.toFloat() * scale)
            val dx = (L - scaledWidth) / 2
            val dy = (B - scaledHeight) / 2

            val matrix = android.graphics.Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)

            val rect = RectF(
                boundingBox.left.toFloat(),
                boundingBox.top.toFloat(),
                boundingBox.right.toFloat(),
                boundingBox.bottom.toFloat()
            )
            matrix.mapRect(rect)
            return rect
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
