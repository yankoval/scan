package com.example.scan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String? = null,
    val date: String? = null,
    val lineNum: String? = null,
    val isGroup: Boolean? = null,
    val gtin: String? = null,
    val lotNo: String? = null,
    val expDate: String? = null,
    val addProdInfo: String? = null,
    @SerialName("numРacksInBox")
    val numPacksInBox: Int? = null,
    val numLayersInBox: Int? = null,
    val maxNoRead: Int? = null,
    val urlLabelProductTemplate: String? = null,
    val urlLabelBoxTemplate: String? = null,
    val numLabelAtBox: Int? = null,
    val lengthBox: Double? = null,
    val numPacksInParcel: Int? = null,
    val boxLabelFields: List<BoxLabelField>? = null,
    val productNumbers: List<String>? = null,
    val boxNumbers: List<String>? = null
)
