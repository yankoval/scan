package com.example.scan.scanlogic

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeValidatorTest {

    private val validator = BarcodeValidator()

    @Test
    fun `validateCodes with valid SSCC code`() {
        val codes = listOf("001234567890123456")
        val result = validator.validateCodes(codes)
        assertEquals(1, result.size)
        assertEquals(true, result[0].isValid)
        assertEquals("SSCC", result[0].type)
    }

    @Test
    fun `validateCodes with valid standard GS1 code`() {
        // 01(14) + 21(13) + 91(4) + 92(44) = 83 chars
        val code = "01" + "1".repeat(14) + "21" + "2".repeat(13) + "91" + "3".repeat(4) + "92" + "4".repeat(44)
        val codes = listOf(code)
        val result = validator.validateCodes(codes)
        assertEquals(1, result.size)
        assertEquals(true, result[0].isValid)
        assertEquals("КИН", result[0].type)
    }

    @Test
    fun `validateCodes with valid shortened GS1 code`() {
        // 01(14) + 21(13) + 93(4) = 37 chars
        val code = "01" + "1".repeat(14) + "21" + "2".repeat(13) + "93" + "3".repeat(4)
        val codes = listOf(code)
        val result = validator.validateCodes(codes)
        assertEquals(1, result.size)
        assertEquals(true, result[0].isValid)
        assertEquals("КИН", result[0].type)
    }

    @Test
    fun `validateCodes with invalid code`() {
        val codes = listOf("12345")
        val result = validator.validateCodes(codes)
        assertEquals(1, result.size)
        assertEquals(false, result[0].isValid)
        assertEquals("", result[0].type)
    }

    @Test
    fun `validateCodes with mixed valid and invalid codes`() {
        val validSscc = "001234567890123456"
        val invalidCode = "invalid"
        val validGs1 = "01" + "1".repeat(14) + "21" + "2".repeat(13) + "93" + "3".repeat(4)
        val codes = listOf(validSscc, invalidCode, validGs1)
        val result = validator.validateCodes(codes)

        assertEquals(3, result.size)

        assertEquals(true, result[0].isValid)
        assertEquals("SSCC", result[0].type)

        assertEquals(false, result[1].isValid)
        assertEquals("", result[1].type)

        assertEquals(true, result[2].isValid)
        assertEquals("КИН", result[2].type)
    }

    @Test
    fun `validateCodes with empty list`() {
        val codes = emptyList<String>()
        val result = validator.validateCodes(codes)
        assertEquals(0, result.size)
    }
}
