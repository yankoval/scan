package com.example.scan.model

import kotlinx.serialization.Serializable

@Serializable
data class AggregationReport(
    val id: String,
    val startTime: String,
    val endTime: String,
    val readyBox: List<ReadyBox>
)

@Serializable
data class ReadyBox(
    val Number: Int,
    val boxNumber: String,
    val boxTime: String,
    val productNumbersFull: List<String>
)
