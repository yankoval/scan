package com.example.scan.model

import kotlinx.serialization.Serializable

@Serializable
data class BoxLabelField(
    val FieldName: String,
    val FieldData: String
)
