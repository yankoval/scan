package com.example.scan.report

import com.example.scan.model.AggregatePackage
import com.example.scan.model.Task
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// Data classes for JSON serialization, matching the report format
@Serializable
data class AggregationReport(
    val id: String,
    val startTime: String,
    val endTime: String,
    val operators: List<String> = emptyList(),
    val readyBox: List<ReadyBox>,
    val sampleNumbers: List<String> = emptyList(),
    val sampleNumbersFull: List<String>? = null,
    val defectiveCodes: List<String>? = null,
    val defectiveCodesFull: List<String>? = null,
    val emptyNumbers: List<String>? = null
)

@Serializable
data class ReadyBox(
    val Number: Int,
    val boxNumber: String,
    val boxAgregate: Boolean = true,
    val boxTime: String,
    val productNumbers: List<String> = emptyList(),
    val productNumbersFull: List<String>
)

class AggregationReportGenerator(private val boxStore: BoxStore) {

    private val aggregatePackageBox = boxStore.boxFor<AggregatePackage>()

    fun generateReport(task: Task): String {
        val allPackages = aggregatePackageBox.all

        val readyBoxes = allPackages.mapIndexed { index, pkg ->
            ReadyBox(
                Number = index,
                boxNumber = pkg.sscc,
                boxTime = formatTimestamp(pkg.timestamp),
                productNumbersFull = pkg.codes.map { it.fullCode }
            )
        }

        val report = AggregationReport(
            id = task.id,
            startTime = formatTimestamp(task.startTime),
            endTime = formatTimestamp(System.currentTimeMillis()),
            readyBox = readyBoxes
        )

        val json = Json { prettyPrint = true }
        return json.encodeToString(report)
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timestamp))
    }
}
