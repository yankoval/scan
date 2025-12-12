package com.example.scan.scanlogic

/**
 * Data class representing the result of a barcode validation.
 *
 * @property rawValue The original raw string from the scanner.
 * @property isValid True if the barcode is valid, false otherwise.
 * @property type The type of the barcode, e.g., "КИН", "SSCC", or an empty string if not valid.
 */
data class ValidatedBarcode(
    val rawValue: String,
    val isValid: Boolean,
    val type: String
)
