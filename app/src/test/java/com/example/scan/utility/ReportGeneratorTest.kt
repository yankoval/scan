package com.example.scan.utility

import com.example.scan.model.AggregatePackage
import com.example.scan.model.AggregatedCode
import com.example.scan.model.MyObjectBox
import com.example.scan.model.Task
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReportGeneratorTest {

    private lateinit var store: BoxStore

    @Before
    fun setUp() {
        store = MyObjectBox.builder().directory(File("objectbox-report-test")).build()
    }

    @After
    fun tearDown() {
        store.close()
        store.deleteAllFiles()
    }

    private fun createTask(): Task {
        return Task(
            id = "test-task",
            date = "2023-01-01",
            lineNum = "1",
            isGroup = false,
            gtin = "01234567890123",
            lotNo = "LOT123",
            expDate = "2025-12-31",
            addProdInfo = "",
            numPacksInBox = 1,
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
            startTime = "2023-01-01T10:00:00Z"
        )
    }

    @Test
    fun testGenerateAggregationReport_appliesFilters() {
        val task = createTask()
        val aggregateBox = store.boxFor(AggregatePackage::class.java)
        val codeBox = store.boxFor(AggregatedCode::class.java)

        val pkg = AggregatePackage(sscc = "]C1SSCC123", timestamp = 1672531200000L)
        aggregateBox.put(pkg)

        val code1 = AggregatedCode(fullCode = "\u001dCODE1")
        code1.aggregatePackage.target = pkg
        codeBox.put(code1)

        val report = ReportGenerator.generateAggregationReport(
            task,
            listOf(pkg),
            "^]C1",
            "^\u001d"
        )

        assertEquals(1, report.readyBox.size)
        assertEquals("SSCC123", report.readyBox[0].boxNumber)
        assertEquals(1, report.readyBox[0].productNumbersFull.size)
        assertEquals("CODE1", report.readyBox[0].productNumbersFull[0])
    }
}
