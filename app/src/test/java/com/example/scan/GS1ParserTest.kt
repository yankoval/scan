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
        val result = parser.parse(code)
        assertEquals(expected, result)
    }

    @Test
    fun `test valid gs1-128 code`() {
        val code = "]C100046070517900000056"
        val expected = mapOf("00" to "046070517900000056")
        val result = parser.parse(code)
        assertEquals(expected, result)
    }

    @Test
    fun `test valid ean-13 code`() {
        val code = "4010276020752"
        val expected = mapOf("01" to "4010276020752")
        val result = parser.parse(code)
        assertEquals(expected, result)
    }

    @Test
    fun `test invalid ean-13 code`() {
        val code = "12345"
        val result = parser.parse(code)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test unknown AI`() {
        val code = "99ABC123"
        val result = parser.parse(code)
        // GS1Parser currently stops and returns what it found so far if AI is unknown
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test valid chestny znak datamatrix code`() {
        val code = "\u001d0104610117656289215,IN\"j\u001d934P4Z"
        val expected = mapOf(
            "01" to "04610117656289",
            "21" to "5,IN\"j",
            "93" to "4P4Z"
        )
        val result = parser.parse(code)
        assertEquals(expected, result)
    }
}
