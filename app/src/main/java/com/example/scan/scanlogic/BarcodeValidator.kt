package com.example.scan.scanlogic

class BarcodeValidator {

    companion object {
        private const val SSCC_PREFIX = "00"
        private const val SSCC_LENGTH = 18

        private const val GTIN_AI = "01"
        private const val SERIAL_AI = "21"
        private const val CHECK_KEY_AI = "91"
        private const val CHECK_VALUE_AI = "92"
        private const val SHORT_CHECK_VALUE_AI = "93"

        private const val GTIN_START_INDEX = 0
        private const val GTIN_END_INDEX = 2
        private const val SERIAL_AI_START_INDEX = 16
        private const val SERIAL_AI_END_INDEX = 18
        private const val CHECK_KEY_AI_START_INDEX = 31
        private const val CHECK_KEY_AI_END_INDEX = 33
        private const val CHECK_VALUE_AI_START_INDEX = 37
        private const val CHECK_VALUE_AI_END_INDEX = 39

        private const val GS1_STANDARD_LENGTH = 83
        private const val GS1_SHORTENED_LENGTH = 37
    }

    fun validateCodes(codes: List<String>): List<ValidatedBarcode> {
        return codes.map { validateSingleCode(it) }
    }

    private fun validateSingleCode(code: String): ValidatedBarcode {
        if (isSscc(code)) {
            return ValidatedBarcode(rawValue = code, isValid = true, type = "SSCC")
        }
        if (isStandardGs1(code) || isShortenedGs1(code)) {
            return ValidatedBarcode(rawValue = code, isValid = true, type = "КИН")
        }
        return ValidatedBarcode(rawValue = code, isValid = false, type = "")
    }

    private fun isSscc(code: String): Boolean {
        return code.startsWith(SSCC_PREFIX) && code.length == SSCC_LENGTH
    }

    private fun isStandardGs1(code: String): Boolean {
        return code.length == GS1_STANDARD_LENGTH &&
                code.substring(GTIN_START_INDEX, GTIN_END_INDEX) == GTIN_AI &&
                code.substring(SERIAL_AI_START_INDEX, SERIAL_AI_END_INDEX) == SERIAL_AI &&
                code.substring(CHECK_KEY_AI_START_INDEX, CHECK_KEY_AI_END_INDEX) == CHECK_KEY_AI &&
                code.substring(CHECK_VALUE_AI_START_INDEX, CHECK_VALUE_AI_END_INDEX) == CHECK_VALUE_AI
    }

    private fun isShortenedGs1(code: String): Boolean {
        return code.length == GS1_SHORTENED_LENGTH &&
                code.substring(GTIN_START_INDEX, GTIN_END_INDEX) == GTIN_AI &&
                code.substring(SERIAL_AI_START_INDEX, SERIAL_AI_END_INDEX) == SERIAL_AI &&
                code.substring(CHECK_KEY_AI_START_INDEX, CHECK_KEY_AI_END_INDEX) == SHORT_CHECK_VALUE_AI
    }
}
