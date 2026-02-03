package com.example.scan.utility

import timber.log.Timber

/**
 * Utility for filtering barcode symbols based on regex templates.
 */
object CodeFilter {

    /**
     * Filters the given [code] using the provided [template] regex.
     * Any matches of the regex in the code will be replaced with an empty string.
     *
     * @param code The barcode string to filter.
     * @param template The regex template to use for filtering.
     * @return The filtered code string.
     */
    fun symbologiesSymbolsFilter(code: String, template: String): String {
        if (template.isEmpty()) return code
        return try {
            val regex = Regex(template)
            code.replace(regex, "")
        } catch (e: Exception) {
            Timber.tag("CodeFilter").e(e, "Invalid regex template: %s", template)
            code
        }
    }
}
