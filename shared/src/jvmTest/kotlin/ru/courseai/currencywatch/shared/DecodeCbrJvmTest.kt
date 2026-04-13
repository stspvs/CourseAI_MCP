package ru.courseai.currencywatch.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class DecodeCbrJvmTest {

    @Test
    fun decodeWindows1251() {
        // Буква «й» в windows-1251 = 0xE9; в UTF-8 как два байта — другой набор.
        val bytes = byteArrayOf(0xE9.toByte())
        val s = decodeCbrXmlBytes(bytes)
        assertEquals("й", s)
    }

    @Test
    fun decodeAsciiRoundTrip() {
        val xml = "<?xml version=\"1.0\"?><r/>"
        val bytes = xml.toByteArray(Charsets.US_ASCII)
        assertEquals(xml, decodeCbrXmlBytes(bytes))
    }
}
