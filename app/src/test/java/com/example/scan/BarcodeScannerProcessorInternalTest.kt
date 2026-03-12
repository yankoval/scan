package com.example.scan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.scan.model.MyObjectBox
import com.example.scan.model.ScannedCode
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.barcode.common.Barcode
import io.objectbox.Box
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
class BarcodeScannerProcessorInternalTest {

    private lateinit var store: BoxStore
    private lateinit var processor: BarcodeScannerProcessor
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Initialize ML Kit for tests
        try {
            MlKitContext.initializeIfNeeded(context)
        } catch (e: Exception) {
             // Handle or ignore if already initialized
        }

        val testDir = File("objectbox-internal-test")
        if (testDir.exists()) testDir.deleteRecursively()

        store = MyObjectBox.builder().directory(testDir).build()

        val overlay = mock(GraphicOverlay::class.java)
        val listener = mock(BarcodeScannerProcessor.OnBarcodeScannedListener::class.java)

        processor = BarcodeScannerProcessor(
            overlay,
            context,
            listener,
            store,
            aggregateFilter = "^]C1",
            aggregatedFilter = "^\\u001d"
        )
    }

    @After
    fun tearDown() {
        if (this::store.isInitialized) {
            store.close()
            val testDir = File("objectbox-internal-test")
            if (testDir.exists()) testDir.deleteRecursively()
        }
    }

    @Test
    fun `test checkLogic correctly identifies SSCC in complex GS1 code`() {
        val checkLogicMethod: Method = BarcodeScannerProcessor::class.java.getDeclaredMethod("checkLogic", String::class.java)
        checkLogicMethod.isAccessible = true

        // Example provided by user: SSCC + Batch + etc.
        // Prefix with ]C1 to make it a GS1 code
        val rawValue = "]C1000460705179000007110 10461011765631910BN000759221 112509181325100317270918"

        @Suppress("UNCHECKED_CAST")
        val result = checkLogicMethod.invoke(processor, rawValue) as Pair<String, MutableList<String>>

        assertEquals("GS1_SSCC", result.first)
        assertTrue(result.second.any { it.startsWith("00:046070517900000711") })
    }

    @Test
    fun `test handleBarcodes filters prefix and AI from SSCC`() {
        val handleBarcodesMethod = BarcodeScannerProcessor::class.java.getDeclaredMethod(
            "handleBarcodes",
            List::class.java,
            com.example.scan.model.Task::class.java,
            androidx.camera.core.ImageProxy::class.java
        )
        handleBarcodesMethod.isAccessible = true

        val mockBarcode = mock(Barcode::class.java)
        `when`(mockBarcode.rawValue).thenReturn("]C100046070517900000056")
        `when`(mockBarcode.format).thenReturn(Barcode.FORMAT_CODE_128)

        val mockImageProxy = mock(androidx.camera.core.ImageProxy::class.java)
        `when`(mockImageProxy.width).thenReturn(100)
        `when`(mockImageProxy.height).thenReturn(100)

        handleBarcodesMethod.invoke(processor, listOf(mockBarcode), null, mockImageProxy)

        val scannedCodeBox: Box<ScannedCode> = store.boxFor(ScannedCode::class.java)
        val storedCodes = scannedCodeBox.all
        assertEquals(1, storedCodes.size)
        // Should be 18 digits, no ]C1, no 00 (because gs1Parser extracts value)
        assertEquals("046070517900000056", storedCodes[0].code)
    }
}
