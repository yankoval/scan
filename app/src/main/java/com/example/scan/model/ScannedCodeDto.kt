package com.example.scan.model

import kotlinx.serialization.Serializable

@Serializable
data class ScannedCodeDto(
    val id: Long,
    val code: String,
    val codeType: String,
    val contentType: String,
    val gs1Data: List<String>,
    val timestamp: Long
)
