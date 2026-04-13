package ru.courseai.currencywatch.shared

import ru.courseai.currencywatch.shared.model.ExchangeRateRow

object CbrXmlParser {

    fun parseDailyXml(xml: String, fetchedAtMillis: Long): List<ExchangeRateRow> {
        val dateAttr = Regex("""Date="(\d{2}\.\d{2}\.\d{4})"""")
            .find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?: ""

        val valuteBlocks = Regex("""<Valute[^>]*>([\s\S]*?)</Valute>""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()

        return valuteBlocks.mapNotNull { block ->
            val charCode = tagValue(block, "CharCode") ?: return@mapNotNull null
            val nominal = tagValue(block, "Nominal")?.toIntOrNull() ?: return@mapNotNull null
            val valueStr = tagValue(block, "Value") ?: return@mapNotNull null
            val value = parseRussianDecimal(valueStr) ?: return@mapNotNull null
            val perUnit = value / nominal.toDouble()
            ExchangeRateRow(
                charCode = charCode.trim(),
                nominal = nominal,
                valuePerUnit = perUnit,
                cbrDate = dateAttr,
                fetchedAtMillis = fetchedAtMillis,
            )
        }
    }

    private fun tagValue(block: String, tag: String): String? =
        Regex("<$tag>([^<]*)</$tag>").find(block)?.groupValues?.getOrNull(1)?.trim()

    private fun parseRussianDecimal(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        return normalized.toDoubleOrNull()
    }
}
