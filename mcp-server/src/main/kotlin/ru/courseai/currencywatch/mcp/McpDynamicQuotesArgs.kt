package ru.courseai.currencywatch.mcp

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal sealed interface ParseDynamicQuotesResult {
    data class Success(val valuteId: String, val dateFrom: LocalDate, val dateTo: LocalDate) : ParseDynamicQuotesResult

    data class Failure(val message: String) : ParseDynamicQuotesResult
}

private val cbrValuteIdPattern = Regex("""^R\d+$""", RegexOption.IGNORE_CASE)

/**
 * Разбор аргументов [get_currency_quotes_period]: val_nm_rq, date_from, date_to.
 */
internal fun parseDynamicQuotesArgs(arguments: JsonObject?): ParseDynamicQuotesResult {
    val valRaw = arguments?.get("val_nm_rq")?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim()
    if (valRaw.isNullOrEmpty()) {
        return ParseDynamicQuotesResult.Failure(
            "Укажите параметр val_nm_rq — числовой код валюты ЦБ (например R01235 для USD; код берётся из атрибута ID у <Valute> в ответе XML_daily.asp).",
        )
    }
    val valuteId = valRaw.uppercase()
    if (!cbrValuteIdPattern.matches(valuteId)) {
        return ParseDynamicQuotesResult.Failure(
            "Параметр val_nm_rq должен быть в формате ЦБ: буква R и цифры (например R01235), получено: «$valRaw».",
        )
    }
    val fromRaw = arguments?.get("date_from")?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim()
    val toRaw = arguments?.get("date_to")?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim()
    if (fromRaw.isNullOrEmpty() || toRaw.isNullOrEmpty()) {
        return ParseDynamicQuotesResult.Failure(
            "Укажите date_from и date_to: YYYY-MM-DD, DD.MM.YYYY или YYYYMMDD (как для других инструментов с датами).",
        )
    }
    val dateFrom = parseFlexibleLocalDate(fromRaw)
        ?: return ParseDynamicQuotesResult.Failure(
            "Некорректная date_from «$fromRaw».",
        )
    val dateTo = parseFlexibleLocalDate(toRaw)
        ?: return ParseDynamicQuotesResult.Failure(
            "Некорректная date_to «$toRaw».",
        )
    if (dateFrom > dateTo) {
        return ParseDynamicQuotesResult.Failure(
            "date_from не должна быть позже date_to: $dateFrom > $dateTo.",
        )
    }
    return ParseDynamicQuotesResult.Success(valuteId, dateFrom, dateTo)
}
