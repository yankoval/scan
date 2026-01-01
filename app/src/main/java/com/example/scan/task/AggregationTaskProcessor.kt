package com.example.scan.task

import com.example.scan.model.*
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import timber.log.Timber

class AggregationTaskProcessor(private val boxStore: BoxStore) : ITaskProcessor {

    private val scannedCodeBox: Box<ScannedCode> = boxStore.boxFor()
    private val aggregatePackageBox: Box<AggregatePackage> = boxStore.boxFor()
    private val aggregatedCodeBox: Box<AggregatedCode> = boxStore.boxFor()

    override fun check(codes: List<ScannedCode>, task: Task): Boolean {
        Timber.d("--- Starting Aggregation Check ---")
        Timber.d("Total codes to check: ${codes.size}")

        // 1. Separate codes
        val productCodes = codes.filter { it.contentType == "GS1_DATAMATRIX" }
        val packageCodes = codes.filter { it.contentType == "GS1_SSCC" }
        Timber.d("Found ${productCodes.size} product codes and ${packageCodes.size} package codes.")

        // 2. Check for a single SSCC code
        if (packageCodes.size != 1) {
            Timber.w("CHECK FAILED: Expected 1 SSCC code, but found ${packageCodes.size}.")
            return false
        }
        val sscc = packageCodes.first().code
        Timber.d("SSCC code: $sscc")

        // 3. Check if GTINs match the task
        val taskGtin = task.gtin
        Timber.d("Task GTIN: $taskGtin")
        productCodes.forEach { code ->
            val gtinFromCode = code.gs1Data.firstOrNull { it.startsWith("01:") }?.substring(3)
            Timber.d("Checking code '${code.code}' with GTIN '$gtinFromCode'")
            if (gtinFromCode != taskGtin) {
                Timber.w("CHECK FAILED: Mismatched GTIN. Task requires '$taskGtin', but found '$gtinFromCode'.")
                return false
            }
        }
        Timber.d("GTIN check passed.")

        // 4. Check if the number of product codes matches numPacksInBox
        val numPacksInBox = task.numPacksInBox
        val distinctProductCodes = productCodes.distinctBy { it.code }
        Timber.d("Task requires $numPacksInBox product codes. Found ${distinctProductCodes.size} distinct product codes.")
        if (distinctProductCodes.size != numPacksInBox) {
            Timber.w("CHECK FAILED: Code count mismatch.")
            return false
        }
        Timber.d("Product code count check passed.")

        // 5. Check for uniqueness in the aggregation database
        Timber.d("Checking for SSCC uniqueness...")
        val existingPackage = aggregatePackageBox.query(AggregatePackage_.sscc.equal(sscc)).build().findFirst()
        if (existingPackage != null) {
            Timber.w("CHECK FAILED: SSCC '$sscc' already exists.")
            return false
        }
        Timber.d("SSCC uniqueness check passed.")

        Timber.d("Checking for product code uniqueness...")
        for (productCode in distinctProductCodes) {
            val existingCode = aggregatedCodeBox.query(AggregatedCode_.fullCode.equal(productCode.code)).build().findFirst()
            if (existingCode != null) {
                Timber.w("CHECK FAILED: Product code '${productCode.code}' already exists in the database.")
                return false
            }
        }
        Timber.d("Product code uniqueness check passed.")

        // All checks passed
        Timber.d("--- All Checks Passed ---")
        Timber.d("Preparing to aggregate package with SSCC '$sscc'.")

        val newPackage = AggregatePackage(sscc = sscc)
        val newAggregatedCodes = distinctProductCodes.map {
            val gtin = it.gs1Data.first { data -> data.startsWith("01:") }.substring(3)
            val serial = it.gs1Data.first { data -> data.startsWith("21:") }.substring(3)
            AggregatedCode(fullCode = it.code, gtin = gtin, serialNumber = serial).apply {
                this.aggregatePackage.target = newPackage
            }
        }
        Timber.d("Created ${newAggregatedCodes.size} new AggregatedCode entities.")

        try {
            Timber.d("Starting database transaction...")
            boxStore.runInTx {
                Timber.d("Inside transaction: Putting AggregatePackage...")
                aggregatePackageBox.put(newPackage)
                Timber.d("Inside transaction: Putting AggregatedCodes...")
                aggregatedCodeBox.put(newAggregatedCodes)
                Timber.d("Inside transaction: Removing old ScannedCodes...")
                scannedCodeBox.removeAll()
                Timber.d("Inside transaction: Comitting...")
            }
            Timber.d("Database transaction successful.")
        } catch (e: Exception) {
            Timber.e(e, "DATABASE TRANSACTION FAILED")
            // Re-throw or handle the exception as needed. For now, just log and fail.
            return false
        }

        Timber.d("--- Aggregation Check Successful ---")
        return true
    }
}