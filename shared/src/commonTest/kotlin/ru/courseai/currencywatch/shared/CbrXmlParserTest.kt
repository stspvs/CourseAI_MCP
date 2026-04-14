package ru.courseai.currencywatch.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun missingCharCodeSkipsBlock() {
        val xml = cbrDailyXml(
            "07.07.2024",
            """
            <Valute>
            <Nominal>1</Nominal>
            <Value>1,0</Value>
            </Valute>
            """.trimIndent(),
            valuteBlock("Z", 1, "2,0"),
        )
        val rows = CbrXmlParser.parseDailyXml(xml, 0L)
        assertEquals(1, rows.size)
        assertEquals("Z", rows.single().charCode)
    }

    @Test
    fun parsesMultipleValutes() {
        val xml = cbrDailyXml(
            "08.08.2024",
            valuteBlock("USD", 1, "10,0"),
            valuteBlock("EUR", 1, "20,0"),
        )
        val codes = CbrXmlParser.parseDailyXml(xml, 0L).map { it.charCode }.sorted()
        assertEquals(listOf("EUR", "USD"), codes)
    }

    @Test
    fun parseDynamicXmlReadsRecordsAndMeta() {
        val xml = """
            <?xml version="1.0" encoding="windows-1251"?>
            <ValCurs ID="R01235" DateRange1="02.03.2001" DateRange2="14.03.2001" name="USD">
            <Record Date="02.03.2001">28,6200</Record>
            <Record Date="05.03.2001">28,7000</Record>
            </ValCurs>
        """.trimIndent()
        val s = CbrXmlParser.parseDynamicXml(xml)
        requireNotNull(s)
        assertEquals("R01235", s.valuteId)
        assertEquals("02.03.2001", s.dateRange1)
        assertEquals("14.03.2001", s.dateRange2)
        assertEquals("USD", s.name)
        assertEquals(2, s.records.size)
        assertEquals("02.03.2001", s.records[0].recordDate)
        assertEquals(28.62, s.records[0].valueRub, 1e-9)
        assertEquals(28.7, s.records[1].valueRub, 1e-9)
    }

    @Test
    fun parseDynamicXmlReadsNestedValuePerCbrCurrentFormat() {
        val xml = """
            <ValCurs ID="R01239" DateRange1="01.03.2025" DateRange2="31.03.2025" name="Foreign Currency Market Dynamic">
            <Record Date="01.03.2025" Id="R01239">
            <Nominal>1</Nominal>
            <Value>91,5655</Value>
            <VunitRate>91,5655</VunitRate>
            </Record>
            <Record Date="04.03.2025" Id="R01239">
            <Nominal>1</Nominal>
            <Value>92,8530</Value>
            </Record>
            </ValCurs>
        """.trimIndent()
        val s = CbrXmlParser.parseDynamicXml(xml)
        requireNotNull(s)
        assertEquals(2, s.records.size)
        assertEquals(91.5655, s.records[0].valueRub, 1e-9)
        assertEquals(92.853, s.records[1].valueRub, 1e-9)
    }

    @Test
    fun parseDynamicXmlUsesValueOverNominalForPerUnit() {
        val xml = """
            <ValCurs ID="R01370" DateRange1="01.03.2026" DateRange2="05.03.2026" name="test">
            <Record Date="03.03.2026" Id="R01370">
            <Nominal>100</Nominal>
            <Value>88,2534</Value>
            <VunitRate>0,882534</VunitRate>
            </Record>
            </ValCurs>
        """.trimIndent()
        val s = requireNotNull(CbrXmlParser.parseDynamicXml(xml))
        assertEquals(1, s.records.size)
        assertEquals(0.882534, s.records[0].valueRub, 1e-9)
    }

    @Test
    fun parseDynamicXmlTakesLastValueWhenDuplicatesInRecord() {
        val xml = """
            <ValCurs ID="R01235" DateRange1="01.03.2026" DateRange2="05.03.2026" name="x">
            <Record Date="03.03.2026" Id="R01235">
            <Nominal>1</Nominal>
            <Value>75,7327</Value>
            <Value>77,1734</Value>
            <VunitRate>75,7327</VunitRate>
            </Record>
            </ValCurs>
        """.trimIndent()
        val s = requireNotNull(CbrXmlParser.parseDynamicXml(xml))
        assertEquals(77.1734, s.records[0].valueRub, 1e-9)
    }

    @Test
    fun parseDynamicXmlNullWhenNoValCurs() {
        assertNull(CbrXmlParser.parseDynamicXml("<html></html>"))
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
