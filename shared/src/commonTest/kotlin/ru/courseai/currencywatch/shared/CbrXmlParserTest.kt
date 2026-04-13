package ru.courseai.currencywatch.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CbrXmlParserTest {

    @Test
    fun parsesSingleValuteAndDate() {
        val xml = cbrDailyXml(
            "13.04.2026",
            valuteBlock("USD", 1, "90,1234"),
        )
        val rows = CbrXmlParser.parseDailyXml(xml, fetchedAtMillis = 1_000L)
        assertEquals(1, rows.size)
        val r = rows.single()
        assertEquals("USD", r.charCode)
        assertEquals(1, r.nominal)
        assertEquals(90.1234, r.valuePerUnit, 1e-9)
        assertEquals("13.04.2026", r.cbrDate)
        assertEquals(1_000L, r.fetchedAtMillis)
    }

    @Test
    fun nominalDividesValueForPerUnit() {
        val xml = cbrDailyXml(
            "01.01.2024",
            valuteBlock("JPY", 100, "65,4321"),
        )
        val r = CbrXmlParser.parseDailyXml(xml, 0L).single()
        assertEquals(100, r.nominal)
        assertEquals(0.654321, r.valuePerUnit, 1e-9)
    }

    @Test
    fun skipsIncompleteValuteBlocks() {
        val xml = cbrDailyXml(
            "02.02.2024",
            """
            <Valute>
            <CharCode>BAD</CharCode>
            <Nominal>1</Nominal>
            </Valute>
            """.trimIndent(),
            valuteBlock("OK", 1, "1,0"),
        )
        val rows = CbrXmlParser.parseDailyXml(xml, 0L)
        assertEquals(1, rows.size)
        assertEquals("OK", rows.single().charCode)
    }

    @Test
    fun emptyXmlYieldsEmptyList() {
        assertTrue(CbrXmlParser.parseDailyXml("", 0L).isEmpty())
    }

    @Test
    fun missingDateAttributeYieldsEmptyCbrDate() {
        val xml = """
            <ValCurs>
            ${valuteBlock("USD", 1, "1,0")}
            </ValCurs>
        """.trimIndent()
        val r = CbrXmlParser.parseDailyXml(xml, 0L).single()
        assertEquals("", r.cbrDate)
    }

    @Test
    fun whitespaceInCharCodeIsTrimmed() {
        val xml = cbrDailyXml(
            "03.03.2024",
            """
            <Valute>
            <CharCode> EUR </CharCode>
            <Nominal>1</Nominal>
            <Value>100,0</Value>
            </Valute>
            """.trimIndent(),
        )
        assertEquals("EUR", CbrXmlParser.parseDailyXml(xml, 0L).single().charCode)
    }

    @Test
    fun dotDecimalAlsoParsed() {
        val xml = cbrDailyXml("04.04.2024", valuteBlock("CHF", 1, " 99.5 "))
        val r = CbrXmlParser.parseDailyXml(xml, 0L).single()
        assertEquals(99.5, r.valuePerUnit, 1e-9)
    }

    @Test
    fun invalidNominalSkipsBlock() {
        val xml = cbrDailyXml(
            "05.05.2024",
            """
            <Valute>
            <CharCode>X</CharCode>
            <Nominal>oops</Nominal>
            <Value>1,0</Value>
            </Valute>
            """.trimIndent(),
            valuteBlock("Y", 1, "2,0"),
        )
        val rows = CbrXmlParser.parseDailyXml(xml, 0L)
        assertEquals(1, rows.size)
        assertEquals("Y", rows.single().charCode)
    }

    @Test
    fun invalidValueSkipsBlock() {
        val xml = cbrDailyXml(
            "06.06.2024",
            valuteBlock("A", 1, "not-a-number"),
            valuteBlock("B", 1, "3,0"),
        )
        val rows = CbrXmlParser.parseDailyXml(xml, 0L)
        assertEquals(1, rows.size)
        assertEquals("B", rows.single().charCode)
    }

    companion object {
        fun cbrDailyXml(date: String, vararg valuteInner: String): String =
            """
            <ValCurs Date="$date" name="test">
            ${valuteInner.joinToString("\n")}
            </ValCurs>
            """.trimIndent()

        fun valuteBlock(charCode: String, nominal: Int, value: String): String =
            """
            <Valute>
            <CharCode>$charCode</CharCode>
            <Nominal>$nominal</Nominal>
            <Value>$value</Value>
            </Valute>
            """.trimIndent()
    }
}
