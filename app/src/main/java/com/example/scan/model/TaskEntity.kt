package com.example.scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class TaskEntity(
    @Id var id: Long = 0,
    var json: String = ""
)
