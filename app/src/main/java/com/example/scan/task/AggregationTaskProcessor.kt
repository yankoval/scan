package com.example.scan.task

import com.example.scan.model.*
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import timber.log.Timber

class AggregationTaskProcessor(boxStore: BoxStore) : ITaskProcessor {

    private val scannedCodeBox: Box<ScannedCode> = boxStore.boxFor()
    private val aggregatePackageBox: Box<AggregatePackage> = boxStore.boxFor()
    private val aggregatedCodeBox: Box<AggregatedCode> = boxStore.boxFor()

    override fun check(codes: List<ScannedCode>, task: Task): Boolean {
        Timber.d("Starting aggregation check...")
        // 1. Separate codes into product codes (DataMatrix) and package codes (SSCC)
        val productCodes = codes.filter { it.contentType == "GS1_DATAMATRIX" }
        val packageCodes = codes.filter { it.contentType == "GS1_SSCC" }

        // 2. Check for a single SSCC code
        if (packageCodes.size != 1) {
            Timber.d("Aggregation failed: Found ${packageCodes.size} SSCC codes, expected 1.")
            return false
        }
        val sscc = packageCodes.first().code

        // 3. Check if GTINs match the task
        val taskGtin = task.gtin
        productCodes.forEach { code ->
            val gtinFromCode = code.gs1Data.firstOrNull { it.startsWith("01:") }?.substring(3)
            if (gtinFromCode != taskGtin) {
                Timber.d("Aggregation failed: Mismatched GTIN. Task requires '$taskGtin', but found '$gtinFromCode' in code '${code.code}'.")
                return false
            }
        }

        // 4. Check if the number of product codes matches numPacksInBox
        val numPacksInBox = task.numPacksInBox
        val distinctProductCodes = productCodes.distinctBy { it.code }
        if (distinctProductCodes.size != numPacksInBox) {
            Timber.d("Aggregation failed: Code count mismatch. Task requires $numPacksInBox product codes, but found ${distinctProductCodes.size} distinct codes.")
            return false
        }

        // 5. Check for uniqueness in the aggregation database
        val existingPackage = aggregatePackageBox.query(AggregatePackage_.sscc.equal(sscc)).build().findFirst()
        if (existingPackage != null) {
            Timber.d("Aggregation failed: SSCC code '$sscc' already exists in the database.")
            return false
        }

        val allProductFullCodes = distinctProductCodes.map { it.code }
        val existingCodes = aggregatedCodeBox.query(AggregatedCode_.fullCode.`in`(allProductFullCodes.toTypedArray())).build().find()
        if (existingCodes.isNotEmpty()) {
            val foundCodes = existingCodes.joinToString(", ") { "'${it.fullCode}'" }
            Timber.d("Aggregation failed: The following product codes already exist in the database: $foundCodes.")
            return false
        }

        // All checks passed, proceed with aggregation
        Timber.d("Success: All checks passed. Aggregating package with SSCC '$sscc'.")
        val newPackage = AggregatePackage(sscc = sscc)
        val newAggregatedCodes = distinctProductCodes.map {
            val gtin = it.gs1Data.first { data -> data.startsWith("01:") }.substring(3)
            val serial = it.gs1Data.first { data -> data.startsWith("21:") }.substring(3)
            AggregatedCode(fullCode = it.code, gtin = gtin, serialNumber = serial).apply {
                this.aggregatePackage.target = newPackage
            }
        }

        aggregatePackageBox.put(newPackage)
        aggregatedCodeBox.put(newAggregatedCodes)

        scannedCodeBox.removeAll()

        return true
    }
}