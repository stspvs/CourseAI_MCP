package ru.courseai.currencywatch.mcp

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/** Разбор аргументов инструмента `get_currency_rates_on_date` — покрывается юнит-тестами без MCP. */
internal sealed interface ParseCurrencyRatesOnDateResult {
    data class Success(val currencies: Set<String>, val date: LocalDate) : ParseCurrencyRatesOnDateResult

    data class Failure(val message: String) : ParseCurrencyRatesOnDateResult
}

internal fun parseCurrencyRatesOnDateArgs(arguments: JsonObject?): ParseCurrencyRatesOnDateResult {
    val currencies = arguments?.get("currencies")?.jsonArray?.toCurrencyCodeStringSet()
    if (currencies.isNullOrEmpty()) {
        return ParseCurrencyRatesOnDateResult.Failure(
            "Укажите непустой массив currencies с кодами валют (например [\"USD\"] или [\"USD\",\"EUR\"]).",
        )
    }
    val dateRaw = extractDateString(arguments)
    if (dateRaw.isNullOrEmpty()) {
        return ParseCurrencyRatesOnDateResult.Failure(
            "Укажите параметр date: строка YYYY-MM-DD (например 2024-06-01) или DD.MM.YYYY (например 01.06.2024). Число в JSON допустимо (например 20240601).",
        )
    }
    val date = parseFlexibleLocalDate(dateRaw)
        ?: return ParseCurrencyRatesOnDateResult.Failure(
            "Некорректная дата «$dateRaw». Ожидается YYYY-MM-DD или DD.MM.YYYY (например 2024-06-01 или 01.06.2024).",
        )
    return ParseCurrencyRatesOnDateResult.Success(currencies, date)
}

/** Берёт date из объекта: не падает на JsonNull; число в JSON даёт строку через [JsonPrimitive.content], не через contentOrNull. */
private fun extractDateString(arguments: JsonObject?): String? {
    val el = arguments?.get("date") ?: return null
    return when (el) {
        is JsonNull -> null
        is JsonPrimitive -> el.content.trim().takeIf { it.isNotEmpty() }
        else -> null
    }
}

/**
 * ISO YYYY-MM-DD, вариант DD.MM.YYYY, компактное YYYYMMDD (8 цифр).
 */
internal fun parseFlexibleLocalDate(raw: String): LocalDate? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    try {
        return LocalDate.parse(t)
    } catch (_: IllegalArgumentException) {
        // DD.MM.YYYY
        val dot = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""").find(t)
        if (dot != null) {
            val d = dot.groupValues[1].toInt()
            val m = dot.groupValues[2].toInt()
            val y = dot.groupValues[3].toInt()
            return try {
                LocalDate(y, m, d)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        // YYYYMMDD
        val compact = Regex("""^(\d{8})$""").find(t)
        if (compact != null) {
            val s = compact.groupValues[1]
            val y = s.substring(0, 4).toInt()
            val m = s.substring(4, 6).toInt()
            val d = s.substring(6, 8).toInt()
            return try {
                LocalDate(y, m, d)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return null
    }
}
