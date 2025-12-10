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

    // A simple test implementation of the abstract Graphic class to access calculateRect
    private class TestGraphic(overlay: GraphicOverlay) : GraphicOverlay.Graphic(overlay) {
        override fun draw(canvas: Canvas) {
            // Not needed for this test
        }

        fun testCalculateRect(boundingBox: Rect): RectF {
            return calculateRect(boundingBox)
        }
    }

    @Test
    fun `calculateRect should correctly transform coordinates for the back camera`() {
        // 1. Define the input conditions
        val imageWidth = 1280
        val imageHeight = 720
        val viewWidth = 1080
        val viewHeight = 1920

        graphicOverlay.layout(0, 0, viewWidth, viewHeight)
        graphicOverlay.setCameraInfo(imageWidth, imageHeight, CameraSelector.LENS_FACING_BACK)

        val boundingBox = Rect(300, 400, 500, 600)

        // 2. Calculate the expected output
        val scale = min(viewWidth.toFloat() / imageHeight, viewHeight.toFloat() / imageWidth)
        val offsetX = (viewWidth - imageHeight * scale) / 2.0f
        val offsetY = (viewHeight - imageWidth * scale) / 2.0f

        val expectedLeft = boundingBox.top * scale + offsetX
        val expectedTop = boundingBox.left * scale + offsetY
        val expectedRight = boundingBox.bottom * scale + offsetX
        val expectedBottom = boundingBox.right * scale + offsetY

        val expectedRect = RectF(expectedLeft, expectedTop, expectedRight, expectedBottom)

        // 3. Call the method under test
        val testGraphic = TestGraphic(graphicOverlay)
        val actualRect = testGraphic.testCalculateRect(boundingBox)

        // 4. Assert that the actual output matches the expected output
        assertEquals(expectedRect.left, actualRect.left, 0.1f)
        assertEquals(expectedRect.top, actualRect.top, 0.1f)
        assertEquals(expectedRect.right, actualRect.right, 0.1f)
        assertEquals(expectedRect.bottom, actualRect.bottom, 0.1f)
    }
}
