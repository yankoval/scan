package com.example.scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class ScannedCode(
    @Id
    var id: Long = 0,
    var code: String = "",
    @Index
    var codeType: String = "",
    @Index
    var contentType: String = "",
    var gs1Data: MutableList<String> = mutableListOf(),
    var timestamp: Long = 0
)
