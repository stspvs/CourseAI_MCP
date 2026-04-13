package ru.courseai.currencywatch.mcp

import ru.courseai.currencywatch.shared.model.CurrencySummary
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot

/**
 * Тексты ответов инструментов MCP (без I/O) — удобно покрывать юнит-тестами.
 */
internal object McpSummaryMessages {

    fun formatLatestRatesText(rates: List<ExchangeRateSnapshot>): String {
        if (rates.isEmpty()) {
            return "Нет сохранённых курсов. Дождитесь синхронизации с ЦБ или перезапустите сервер."
        }
        val lines = rates.joinToString("\n") {
            "${it.charCode}: ${it.valuePerUnit} RUB за ${it.nominal} ед., дата ЦБ ${it.cbrDate}, загружено (epoch ms)=${it.fetchedAtMillis}"
        }
        return "Последние курсы по валютам:\n$lines"
    }

    fun formatSummaryText(summaries: List<CurrencySummary>, hours: Int): String {
        val lines = summaries.joinToString("\n") {
            "${it.charCode}: avg=${"%.4f".format(it.avg)} min=${"%.4f".format(it.min)} max=${"%.4f".format(it.max)} Δ=${"%.4f".format(it.change)} (n=${it.sampleCount})"
        }
        return "Период: $hours ч.\n$lines"
    }

    fun requestedCurrenciesDisplay(requestedCurrencies: Set<String>?): String =
        requestedCurrencies.orEmpty()
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")

    fun emptySummaryCurrencyMismatch(
        hours: Int,
        totalInWindow: Int,
        requestedCurrencies: Set<String>?,
        availableCodes: List<String>,
    ): String {
        val requestedDisplay = requestedCurrenciesDisplay(requestedCurrencies)
        val availableDisplay = availableCodes.joinToString(", ")
        return buildString {
            append("Нет данных за последние $hours ч. для запрошенных кодов: [$requestedDisplay]. ")
            append("Таких CharCode нет среди записей в окне ($totalInWindow строк). ")
            append("Коды валют, которые реально есть в БД за этот период (${availableCodes.size}): $availableDisplay. ")
            append("Повторите вызов с нужными кодами из этого списка или вызовите инструмент без параметра currencies — тогда вернётся сводка по всем валютам в окне.")
        }
    }

    fun emptySummaryStaleWindow(hours: Int, ageHours: Long): String =
        "Нет записей за последние $hours ч. Сводка считается по времени загрузки в локальную БД (не по календарной дате курса ЦБ). Последняя загрузка была ~$ageHours ч. назад. Увеличьте параметр hours (например 168) или выполните синхронизацию с ЦБ (планировщик, перезапуск сервера)."

    fun emptySummaryNoRowsInDb(hours: Int): String =
        "Нет данных за последние $hours ч. Локальная БД пуста или синхронизация с ЦБ не вернула строк — см. логи (строка «Первая синхронизация: … сохранено N строк»). Если N=0, проверьте доступ к https://www.cbr.ru/scripts/XML_daily.asp"
}
