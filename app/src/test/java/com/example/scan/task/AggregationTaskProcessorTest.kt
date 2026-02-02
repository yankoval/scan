package com.example.scan.task

import com.example.scan.model.AggregatePackage
import com.example.scan.model.MyObjectBox
import com.example.scan.model.ScannedCode
import com.example.scan.model.Task
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AggregationTaskProcessorTest {

    private lateinit var store: BoxStore
    private lateinit var processor: AggregationTaskProcessor

    @Before
    fun setUp() {
        // Use an in-memory database for testing
        store = MyObjectBox.builder().directory(File("objectbox-test")).build()
        processor = AggregationTaskProcessor(store)
    }

    @After
    fun tearDown() {
        store.close()
        store.deleteAllFiles()
    }

    private fun createTask(gtin: String, numPacks: Int): Task {
        return Task(
            id = "test-task",
            date = "2023-01-01",
            lineNum = "1",
            isGroup = false,
            gtin = gtin,
            lotNo = "LOT123",
            expDate = "2025-12-31",
            addProdInfo = "",
            numPacksInBox = numPacks,
            numLayersInBox = 1,
            maxNoRead = 0,
            urlLabelProductTemplate = "",
            urlLabelBoxTemplate = "",
            numLabelAtBox = 1,
            lengthBox = 0.0,
            numPacksInParcel = 0,
            boxLabelFields = emptyList(),
            productNumbers = emptyList(),
            boxNumbers = emptyList(),
            startTime = "2023-01-01T12:00:00Z"
        )
    }

    private fun createProductCode(gtin: String, serial: String): ScannedCode {
        return ScannedCode(
            code = "\u001d01${gtin}21${serial}",
            contentType = "GS1_DATAMATRIX",
            gs1Data = mutableListOf("01:$gtin", "21:$serial")
        )
    }

    private fun createSscc(sscc: String): ScannedCode {
        return ScannedCode(
            code = sscc,
            contentType = "GS1_SSCC",
            gs1Data = mutableListOf("00:$sscc")
        )
    }

    @Test
    fun `check should succeed with correct codes`() {
        val task = createTask("01234567890123", 2)
        val codes = listOf(
            createProductCode("01234567890123", "SERIAL1"),
            createProductCode("01234567890123", "SERIAL2"),
            createSscc("SSCC123456789")
        )

        val result = processor.check(codes, task)

        assertTrue(result is CheckResult.Success)
        val aggregatePackageBox = store.boxFor(AggregatePackage::class.java)
        assertEquals(1, aggregatePackageBox.count())
        assertEquals("SSCC123456789", aggregatePackageBox.all[0].sscc)
    }

    @Test
    fun `check should fail with duplicate product code`() {
        val task = createTask("01234567890123", 2)
        val codes = listOf(
            createProductCode("01234567890123", "SERIAL1"),
            createProductCode("01234567890123", "SERIAL1"), // Duplicate
            createSscc("SSCC123456789")
        )

        val result = processor.check(codes, task)

        assertTrue(result is CheckResult.Failure)
        assertEquals("CHECK FAILED: Code count mismatch. Expected 2, found 1", (result as CheckResult.Failure).reason)
    }

    @Test
    fun `check should fail with duplicate SSCC`() {
        val task = createTask("01234567890123", 1)

        // First, successful aggregation
        val firstCodes = listOf(createProductCode("01234567890123", "SERIAL1"), createSscc("DUPLICATE_SSCC"))
        processor.check(firstCodes, task)

        // Attempt aggregation with the same SSCC
        val secondCodes = listOf(createProductCode("01234567890123", "SERIAL2"), createSscc("DUPLICATE_SSCC"))
        val result = processor.check(secondCodes, task)

        assertTrue(result is CheckResult.Failure)
        assertEquals("CHECK FAILED: SSCC 'DUPLICATE_SSCC' already exists.", (result as CheckResult.Failure).reason)
    }

    @Test
    fun `check should fail with incorrect code count`() {
        val task = createTask("01234567890123", 2)
        val codes = listOf(
            createProductCode("01234567890123", "SERIAL1"),
            createSscc("SSCC123456789")
        )

        val result = processor.check(codes, task)

        assertTrue(result is CheckResult.Failure)
        assertEquals("CHECK FAILED: Code count mismatch. Expected 2, found 1", (result as CheckResult.Failure).reason)
    }

    @Test
    fun `check should fail with mismatched GTIN`() {
        val task = createTask("01234567890123", 2)
        val codes = listOf(
            createProductCode("01234567890123", "SERIAL1"),
            createProductCode("99999999999999", "SERIAL2"), // Mismatched GTIN
            createSscc("SSCC123456789")
        )

        val result = processor.check(codes, task)

        assertTrue(result is CheckResult.Failure)
        assertEquals("Mismatched GTIN", (result as CheckResult.Failure).reason)
    }
}
