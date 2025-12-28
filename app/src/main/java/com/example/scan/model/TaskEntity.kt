package com.example.scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class TaskEntity(
    @Id var id: Long = 1, // Always use the same ID to ensure only one task is stored
    val json: String
)
