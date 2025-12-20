package com.example.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
class GraphicOverlayTest {

    private lateinit var graphicOverlay: GraphicOverlay

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graphicOverlay = GraphicOverlay(context, null)
    }

    private class TestGraphic(overlay: GraphicOverlay) : GraphicOverlay.Graphic(overlay) {
        override fun draw(canvas: Canvas) {}
        fun testCalculateRect(boundingBox: Rect): RectF = calculateRect(boundingBox)
    }

    @Test
    fun `calculateRect should scale and center coordinates correctly`() {
        val imageWidth = 1280
        val imageHeight = 720
        val viewWidth = 1080
        val viewHeight = 1920

        graphicOverlay.layout(0, 0, viewWidth, viewHeight)
        graphicOverlay.setCameraInfo(imageWidth, imageHeight, CameraSelector.LENS_FACING_BACK)

        val boundingBox = Rect(300, 400, 500, 600)

        val scaleX = viewWidth.toFloat() / imageWidth.toFloat()
        val scaleY = viewHeight.toFloat() / imageHeight.toFloat()
        val scale = min(scaleX, scaleY)

        val offsetX = (viewWidth - imageWidth * scale) / 2.0f
        val offsetY = (viewHeight - imageHeight * scale) / 2.0f

        val expectedLeft = boundingBox.left * scale + offsetX
        val expectedTop = boundingBox.top * scale + offsetY
        val expectedRight = boundingBox.right * scale + offsetX
        val expectedBottom = boundingBox.bottom * scale + offsetY

        val expectedRect = RectF(expectedLeft, expectedTop, expectedRight, expectedBottom)

        val testGraphic = TestGraphic(graphicOverlay)
        val actualRect = testGraphic.testCalculateRect(boundingBox)

        assertEquals(expectedRect.left, actualRect.left, 0.1f)
        assertEquals(expectedRect.top, actualRect.top, 0.1f)
        assertEquals(expectedRect.right, actualRect.right, 0.1f)
        assertEquals(expectedRect.bottom, actualRect.bottom, 0.1f)
    }
}
