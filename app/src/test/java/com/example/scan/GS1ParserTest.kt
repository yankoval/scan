package com.example.scan

import org.junit.Assert.*
import org.junit.Test

class GS1ParserTest {

    private val parser = GS1Parser()

    @Test
    fun `test valid datamatrix code`() {
        val code = "\u001d0104600605032541215B,LN)\u001d93gJXT"
        val expected = mapOf(
            "01" to "04600605032541",
            "21" to "5B,LN)",
            "93" to "gJXT"
        )
        val result = parser.parse(code, "DataMatrix")
        assertEquals(expected, result)
    }

    @Test
    fun `test valid gs1-128 code`() {
        val code = "]C100046070517900000056"
        val expected = mapOf("00" to "046070517900000056")
        val result = parser.parse(code, "Code128")
        assertEquals(expected, result)
    }

    @Test
    fun `test valid ean-13 code`() {
        val code = "4010276020752"
        val expected = mapOf("01" to "4010276020752")
        val result = parser.parse(code, "EAN-13")
        assertEquals(expected, result)
    }

    @Test(expected = GS1Parser.GS1ParseException::class)
    fun `test invalid ean-13 code`() {
        val code = "12345"
        parser.parse(code, "EAN-13")
    }

    @Test(expected = GS1Parser.GS1ParseException::class)
    fun `test unknown AI`() {
        val code = "99ABC123"
        parser.parse(code, "DataMatrix")
    }
}
