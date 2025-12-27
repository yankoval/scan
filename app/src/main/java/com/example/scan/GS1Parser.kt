package com.example.scan

class GS1Parser {

    // A map of Application Identifiers (AIs) and their lengths.
    // A length of 0 indicates a variable length field.
    private val aiLengths = mapOf(
        "00" to 18, "01" to 14, "02" to 14, "10" to 0, "11" to 6, "13" to 6,
        "15" to 6, "17" to 6, "21" to 0, "30" to 0, "37" to 0, "414" to 16
        // Add more AIs as needed
    )

    // FNC1 separator for variable length fields
    private val FNC1 = "\u001D"

    class GS1ParseException(message: String) : Exception(message)

    fun parse(code: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var remainingCode = code

        while (remainingCode.isNotEmpty()) {
            var ai: String? = null
            var length: Int? = null

            // Find the matching AI definition
            for (key in aiLengths.keys) {
                if (remainingCode.startsWith(key)) {
                    ai = key
                    length = aiLengths[key]
                    break
                }
            }

            if (ai == null || length == null) {
                throw GS1ParseException("Unknown AI or malformed code at the beginning of: $remainingCode")
            }

            remainingCode = remainingCode.substring(ai.length)

            val value: String
            if (length > 0) {
                // Fixed length AI
                if (remainingCode.length < length) {
                    throw GS1ParseException("Insufficient data for AI '$ai'. Expected length $length, got ${remainingCode.length}")
                }
                value = remainingCode.substring(0, length)
                remainingCode = remainingCode.substring(length)
            } else {
                // Variable length AI
                val fnc1Index = remainingCode.indexOf(FNC1)
                if (fnc1Index != -1) {
                    value = remainingCode.substring(0, fnc1Index)
                    remainingCode = remainingCode.substring(fnc1Index + 1)
                } else {
                    // If no FNC1, the rest of the string is the value
                    value = remainingCode
                    remainingCode = ""
                }
            }

            if (result.containsKey(ai)) {
                throw GS1ParseException("Duplicate AI found: '$ai'")
            }
            result[ai] = value
        }

        return result
    }
}
