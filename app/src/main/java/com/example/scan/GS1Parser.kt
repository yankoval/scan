package com.example.scan

import android.content.Context
import timber.log.Timber

class GS1Parser(private val context: Context) {
    private val aiDefinitions = mapOf(
        "00" to "SSCC",
        "01" to "GTIN",
        "10" to "Batch/Lot Number",
        "11" to "Production Date",
        "15" to "Best Before Date",
        "17" to "Expiration Date",
        "21" to "Serial Number",
        "93" to "Crypto Hash"
        // ... add other AIs as needed
    )

    fun parse(data: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var remainingData = data.replace("]C1", "").replace("\u001d", "")

        // Handle EAN-13 as a special case for GTIN
        if (remainingData.length == 13 && remainingData.all { it.isDigit() }) {
            result["01"] = remainingData
            return result
        }


        while (remainingData.isNotEmpty()) {
            var foundAi = false
            for (aiLength in aiDefinitions.keys.map { it.length }.distinct().sortedDescending()) {
                if (remainingData.length >= aiLength) {
                    val potentialAi = remainingData.substring(0, aiLength)
                    if (aiDefinitions.containsKey(potentialAi)) {
                        val (ai, value, rest) = extractAiData(remainingData, potentialAi)
                        result[ai] = value
                        remainingData = rest
                        foundAi = true
                        break
                    }
                }
            }
            if (!foundAi) {
                Timber.tag("GS1Parser").w("Unknown AI or parsing error at: %s", remainingData)
                break
            }
        }
        return result
    }

    private fun extractAiData(data: String, ai: String): Triple<String, String, String> {
        val variableLengthAis = mapOf(
            "10" to 20, // up to 20 chars
            "21" to 20,  // up to 20 chars
            "93" to -1 // FNC1 terminated, simplified here
        )
        val fixedLengthAis = mapOf(
            "00" to 18,
            "01" to 14,
            "11" to 6,
            "15" to 6,
            "17" to 6
        )

        var value = ""
        var rest = ""

        if (fixedLengthAis.containsKey(ai)) {
            val len = fixedLengthAis[ai]!!
            value = data.substring(ai.length, ai.length + len)
            rest = data.substring(ai.length + len)
        } else if (variableLengthAis.containsKey(ai)) {
            val maxLength = variableLengthAis[ai]!!
            val fnc1Index = data.indexOf('\u001d', ai.length)
            val endIndex = if (fnc1Index != -1) fnc1Index else (ai.length + maxLength).coerceAtMost(data.length)
            value = data.substring(ai.length, endIndex)
            rest = if (fnc1Index != -1) data.substring(endIndex + 1) else data.substring(endIndex)
        }
        return Triple(ai, value, rest)
    }

    fun isSSCC(code: String): Boolean {
        return code.length == 18 && code.all { it.isDigit() }
    }
}
