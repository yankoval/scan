package com.example.scan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class Task(
    val id: String,
    val date: String,
    val lineNum: String,
    val isGroup: Boolean,
    val gtin: String,
    val lotNo: String,
    val expDate: String,
    val addProdInfo: String,
    @JsonNames("numPacksInBox", "numРacksInBox") // Support both Latin 'P' and Cyrillic 'Р'
    val numPacksInBox: Int,
    val numLayersInBox: Int,
    val maxNoRead: Int,
    val urlLabelProductTemplate: String,
    val urlLabelBoxTemplate: String,
    val numLabelAtBox: Int,
    val lengthBox: Double,
    val numPacksInParcel: Int,
    val boxLabelFields: List<BoxLabelField>,
    val productNumbers: List<String>,
    val boxNumbers: List<String>,
    val startTime: String = ""
)
