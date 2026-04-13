package ru.courseai.currencywatch.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/** Нагрузочные сценарии: большой XML и много блоков Valute. */
class CbrXmlParserStressTest {

    @Test
    fun parsesManyValuteBlocks() {
        val n = 2_000
        val parts = ArrayList<String>(n + 2)
        parts.add("""<ValCurs Date="10.10.2024" name="stress">""")
        repeat(n) { i ->
            parts.add(
                """
                <Valute>
                <CharCode>C$i</CharCode>
                <Nominal>1</Nominal>
                <Value>${i % 100},${(i * 7) % 10000}</Value>
                </Valute>
                """.trimIndent(),
            )
        }
        parts.add("</ValCurs>")
        val xml = parts.joinToString("\n")

        val rows = CbrXmlParser.parseDailyXml(xml, fetchedAtMillis = 42L)
        assertEquals(n, rows.size)
        assertEquals("C0", rows.first().charCode)
        assertEquals("C${n - 1}", rows.last().charCode)
        assertEquals(42L, rows.first().fetchedAtMillis)
    }

    @Test
    fun repeatedParseSameLargeXmlIsStable() {
        val inner = buildString {
            repeat(500) { i ->
                append(
                    """
                    <Valute>
                    <CharCode>R$i</CharCode>
                    <Nominal>10</Nominal>
                    <Value>50,${i % 99}</Value>
                    </Valute>
                    """.trimIndent(),
                )
            }
        }
        val xml = """
            <ValCurs Date="11.11.2024" name="r">
            $inner
            </ValCurs>
        """.trimIndent()

        val first = CbrXmlParser.parseDailyXml(xml, 1L)
        val second = CbrXmlParser.parseDailyXml(xml, 1L)
        assertEquals(first.size, second.size)
        assertEquals(first.map { it.charCode }, second.map { it.charCode })
    }
}
