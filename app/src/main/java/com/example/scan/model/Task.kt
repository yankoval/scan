package com.example.scan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Task(
    val id: String,
    val date: String,
    val lineNum: String,
    val isGroup: Boolean,
    val gtin: String,
    @Transient var startTime: Long = 0,
    val lotNo: String,
    val expDate: String,
    val addProdInfo: String,
    @SerialName("numРacksInBox")
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
    val boxNumbers: List<String>
)
