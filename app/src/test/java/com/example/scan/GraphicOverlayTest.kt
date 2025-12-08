package com.example.scan

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GraphicOverlayTest {

    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var testGraphic: GraphicOverlay.Graphic

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graphicOverlay = GraphicOverlay(context, null)

        // Mock the Graphic class to isolate calculateRect for testing
        testGraphic = mock(GraphicOverlay.Graphic::class.java, mock.CALLS_REAL_METHODS)
    }

    @Test
    fun `calculateRect should correctly transform coordinates from landscape to portrait`() {
        // 1. Define the input conditions
        val imageWidth = 1280 // Landscape image width
        val imageHeight = 720  // Landscape image height
        val viewWidth = 1080   // Portrait view width
        val viewHeight = 1920  // Portrait view height

        // Set up the GraphicOverlay with view and image dimensions
        graphicOverlay.layout(0, 0, viewWidth, viewHeight)
        graphicOverlay.setCameraInfo(imageWidth, imageHeight, 0)

        // A sample bounding box from the image analysis (in landscape coordinates)
        val boundingBox = Rect(300, 400, 500, 600) // left, top, right, bottom

        // 2. Calculate the expected output manually
        val scaleX = viewWidth.toFloat() / imageHeight.toFloat()   // 1080 / 720 = 1.5
        val scaleY = viewHeight.toFloat() / imageWidth.toFloat()  // 1920 / 1280 = 1.5
        val scale = minOf(scaleX, scaleY) // 1.5

        val offsetX = (viewWidth.toFloat() - imageHeight.toFloat() * scale) / 2.0f // (1080 - 720 * 1.5) / 2 = 0
        val offsetY = (viewHeight.toFloat() - imageWidth.toFloat() * scale) / 2.0f // (1920 - 1280 * 1.5) / 2 = 0

        val expectedLeft = boundingBox.top * scale + offsetX      // 400 * 1.5 + 0 = 600
        val expectedTop = boundingBox.left * scale + offsetY      // 300 * 1.5 + 0 = 450
        val expectedRight = boundingBox.bottom * scale + offsetX  // 600 * 1.5 + 0 = 900
        val expectedBottom = boundingBox.right * scale + offsetY  // 500 * 1.5 + 0 = 750

        val expectedRect = RectF(expectedLeft, expectedTop, expectedRight, expectedBottom)

        // 3. Call the method under test
        val actualRect = testGraphic.calculateRectForTest(boundingBox)

        // 4. Assert that the actual output matches the expected output
        assertEquals("Left coordinate should be transformed correctly", expectedRect.left, actualRect.left, 0.1f)
        assertEquals("Top coordinate should be transformed correctly", expectedRect.top, actualRect.top, 0.1f)
        assertEquals("Right coordinate should be transformed correctly", expectedRect.right, actualRect.right, 0.1f)
        assertEquals("Bottom coordinate should be transformed correctly", expectedRect.bottom, actualRect.bottom, 0.1f)
    }

    // Helper extension to access the protected method for testing
    private fun GraphicOverlay.Graphic.calculateRectForTest(boundingBox: Rect): RectF {
        return this.calculateRect(boundingBox)
    }
}
