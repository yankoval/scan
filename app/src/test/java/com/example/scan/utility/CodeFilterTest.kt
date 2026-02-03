package com.example.scan.utility

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFilterTest {

    @Test
    fun testSymbologiesSymbolsFilter_removePrefixC1() {
        val code = "]C10012345678901234567"
        val template = "^]C1"
        val expected = "0012345678901234567"
        val result = CodeFilter.symbologiesSymbolsFilter(code, template)
        assertEquals(expected, result)
    }

    @Test
    fun testSymbologiesSymbolsFilter_removePrefixGS() {
        val code = "\u001d0104630014751009215NMLXZ\u001d93yle4"
        val template = "^\u001d"
        val expected = "0104630014751009215NMLXZ\u001d93yle4"
        val result = CodeFilter.symbologiesSymbolsFilter(code, template)
        assertEquals(expected, result)
    }

    @Test
    fun testSymbologiesSymbolsFilter_removePrefixGS_withLiteralTemplate() {
        val code = "\u001d0104630014751009215NMLXZ\u001d93yle4"
        // If the template is literally "^\\u001d" (meaning the characters ^, \, u, 0, 0, 1, d)
        // the Regex engine in Kotlin supports this syntax.
        val template = "^\\u001d"
        val expected = "0104630014751009215NMLXZ\u001d93yle4"
        val result = CodeFilter.symbologiesSymbolsFilter(code, template)
        assertEquals(expected, result)
    }

    @Test
    fun testSymbologiesSymbolsFilter_emptyTemplate() {
        val code = "]C10123"
        val template = ""
        val expected = "]C10123"
        val result = CodeFilter.symbologiesSymbolsFilter(code, template)
        assertEquals(expected, result)
    }

    @Test
    fun testSymbologiesSymbolsFilter_noMatch() {
        val code = "12345"
        val template = "^]C1"
        val expected = "12345"
        val result = CodeFilter.symbologiesSymbolsFilter(code, template)
        assertEquals(expected, result)
    }

    @Test
    fun testSymbologiesSymbolsFilter_invalidRegex() {
        val code = "12345"
        val template = "[" // Invalid regex
        val expected = "12345"
        val result = CodeFilter.symbologiesSymbolsFilter(code, template)
        assertEquals(expected, result)
    }
}
