package com.example.scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class ScannedCode(
    @Id var id: Long = 0,
    val code: String
)
