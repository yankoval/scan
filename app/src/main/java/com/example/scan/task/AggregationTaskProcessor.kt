package com.example.scan.task

import android.util.Log
import com.example.scan.model.*
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor

class AggregationTaskProcessor(boxStore: BoxStore) : ITaskProcessor {

    private val scannedCodeBox: Box<ScannedCode> = boxStore.boxFor()
    private val aggregatePackageBox: Box<AggregatePackage> = boxStore.boxFor()
    private val aggregatedCodeBox: Box<AggregatedCode> = boxStore.boxFor()

    override fun check(codes: List<ScannedCode>, task: Task): Boolean {
        // 1. Separate codes into product codes (DataMatrix) and package codes (SSCC)
        val productCodes = codes.filter { it.contentType == "GS1_DATAMATRIX" }
        val packageCodes = codes.filter { it.contentType == "GS1_SSCC" }

        // 2. Check for a single SSCC code
        if (packageCodes.size != 1) {
            Log.d("AggregationCheck", "Failed: Found ${packageCodes.size} SSCC codes, expected 1.")
            return false
        }
        val sscc = packageCodes.first().code

        // 3. Check if GTINs match the task
        val taskGtin = task.gtin
        val mismatchedGtin = productCodes.any { code ->
            val gtinFromCode = code.gs1Data.firstOrNull { it.startsWith("01") }?.substring(2)
            gtinFromCode != taskGtin
        }
        if (mismatchedGtin) {
            Log.d("AggregationCheck", "Failed: Mismatched GTIN found.")
            return false
        }

        // 4. Check if the number of product codes matches numPacksInBox
        val numPacksInBox = task.numPacksInBox ?: 0
        if (productCodes.distinctBy { it.code }.size != numPacksInBox) {
            Log.d("AggregationCheck", "Failed: Code count (${productCodes.distinctBy { it.code }.size}) does not match numPacksInBox ($numPacksInBox).")
            return false
        }

        // 5. Check for uniqueness in the aggregation database
        val existingPackage = aggregatePackageBox.query(AggregatePackage_.sscc.equal(sscc)).build().findFirst()
        if (existingPackage != null) {
            Log.d("AggregationCheck", "Failed: SSCC code $sscc already exists.")
            return false
        }

        val allProductCodes = productCodes.map { it.code }
        val existingCodesCount = aggregatedCodeBox.query(AggregatedCode_.fullCode.`in`(allProductCodes.toTypedArray())).build().count()
        if (existingCodesCount > 0) {
            Log.d("AggregationCheck", "Failed: Found $existingCodesCount existing product codes in the database.")
            return false
        }

        // All checks passed, proceed with aggregation
        Log.d("AggregationCheck", "Success: All checks passed. Aggregating package.")
        val newPackage = AggregatePackage(sscc = sscc)
        val newAggregatedCodes = productCodes.map {
            val gtin = it.gs1Data.first { data -> data.startsWith("01") }.substring(2)
            val serial = it.gs1Data.first { data -> data.startsWith("21") }.substring(2)
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