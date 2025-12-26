package com.example.scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class ScannedCode(
    @Id var id: Long = 0,
    val code: String,
    @Index val codeType: String = "",
    @Index val contentType: String = "",
    val gs1Data: List<String> = emptyList()
)
