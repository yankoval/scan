package com.example.scan.utility

import com.example.scan.model.AggregatePackage
import com.example.scan.model.AggregationReport
import com.example.scan.model.ReadyBox
import com.example.scan.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Utility for generating aggregation reports.
 */
object ReportGenerator {

    /**
     * Generates an [AggregationReport] from the provided [task] and [packages],
     * applying filters to the codes.
     */
    fun generateAggregationReport(
        task: Task,
        allPackages: List<AggregatePackage>,
        aggregateFilter: String,
        aggregatedFilter: String
    ): AggregationReport {
        val readyBoxes = allPackages.mapIndexed { index, pkg ->
            val filteredSscc = CodeFilter.symbologiesSymbolsFilter(pkg.sscc, aggregateFilter)
            val productCodes = pkg.codes.map {
                CodeFilter.symbologiesSymbolsFilter(it.fullCode, aggregatedFilter)
            }
            ReadyBox(
                Number = index,
                boxNumber = filteredSscc,
                boxTime = formatInstant(pkg.timestamp),
                productNumbersFull = productCodes
            )
        }

        return AggregationReport(
            id = task.id,
            startTime = task.startTime,
            endTime = formatInstant(System.currentTimeMillis()),
            readyBox = readyBoxes
        )
    }

    fun formatInstant(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
