package com.example.scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne

@Entity
data class AggregatedCode(
    @Id var id: Long = 0,
    @Index val fullCode: String, // To check for overall uniqueness
    val gtin: String,
    val serialNumber: String
) {
    lateinit var aggregatePackage: ToOne<AggregatePackage>
}
