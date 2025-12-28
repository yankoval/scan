package com.example.scan.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany

@Entity
data class AggregatePackage(
    @Id var id: Long = 0,
    @Index var sscc: String? = null
) {
    @Backlink(to = "aggregatePackage")
    lateinit var codes: ToMany<AggregatedCode>
}
