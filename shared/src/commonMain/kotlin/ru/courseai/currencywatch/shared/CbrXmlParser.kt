package ru.courseai.currencywatch.shared

import ru.courseai.currencywatch.shared.model.CbrDynamicRecord
import ru.courseai.currencywatch.shared.model.CbrDynamicSeries
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

    /**
     * Парсинг ответа `XML_dynamic.asp`.
     * Старый формат: `<Record Date="DD.MM.YYYY">курс</Record>`.
     * Актуальный формат ЦБ: внутри Record — теги `<Value>`, `<Nominal>`, `<VunitRate>` (см. XSD динамики).
     */
    fun parseDynamicXml(xml: String): CbrDynamicSeries? {
        val root = Regex("""<ValCurs\s+([^>]*)>""", RegexOption.IGNORE_CASE).find(xml) ?: return null
        val attrs = root.groupValues[1]
        val id = attrValue(attrs, "ID") ?: return null
        val dateRange1 = attrValue(attrs, "DateRange1")
        val dateRange2 = attrValue(attrs, "DateRange2")
        val name = attrValue(attrs, "name")

        val recordBlocks = Regex("""<Record\s+([^>]*)>([\s\S]*?)</Record>""", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .mapNotNull { m ->
                val recordAttrs = m.groupValues[1]
                val inner = m.groupValues[2]
                val dateStr = attrValue(recordAttrs, "Date") ?: return@mapNotNull null
                val valueRub = parseDynamicRecordRateRub(inner) ?: return@mapNotNull null
                CbrDynamicRecord(recordDate = dateStr, valueRub = valueRub)
            }
            .toList()

        return CbrDynamicSeries(
            valuteId = id,
            dateRange1 = dateRange1,
            dateRange2 = dateRange2,
            name = name,
            records = recordBlocks,
        )
    }

    private fun attrValue(attrsFragment: String, name: String): String? =
        Regex("${Regex.escape(name)}\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(attrsFragment)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun tagValue(block: String, tag: String): String? =
        Regex("<$tag>([^<]*)</$tag>").find(block)?.groupValues?.getOrNull(1)?.trim()

    /**
     * Курс «за единицу» в рублях: официально Value/Nominal (как в дневном XML). Так отсекаются лишние
     * дубликаты тегов в разметке (при нескольких [Value] берётся последний — актуальный в блоке).
     */
    private fun parseDynamicRecordRateRub(inner: String): Double? {
        val nominal = lastTagValueIgnoreCase(inner, "Nominal")?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val valueBatch = lastTagValueIgnoreCase(inner, "Value")?.let { parseRussianDecimal(it) }
        if (valueBatch != null) {
            return valueBatch / nominal.toDouble()
        }
        val vunit = lastTagValueIgnoreCase(inner, "VunitRate")?.let { parseRussianDecimal(it) }
        if (vunit != null) return vunit
        val flat = inner.trim().takeIf { it.isNotEmpty() && !it.startsWith('<') }
        return flat?.let { parseRussianDecimal(it.trim()) }
    }

    private fun lastTagValueIgnoreCase(block: String, tag: String): String? =
        Regex("<$tag>([^<]*)</$tag>", RegexOption.IGNORE_CASE)
            .findAll(block)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()

    private fun parseRussianDecimal(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        return normalized.toDoubleOrNull()
    }
}
