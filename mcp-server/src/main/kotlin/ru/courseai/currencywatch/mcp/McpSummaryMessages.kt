package ru.courseai.currencywatch.mcp

import kotlinx.datetime.LocalDate
import ru.courseai.currencywatch.shared.model.CbrDynamicSeries
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

    fun formatRatesOnDateText(requestedDate: LocalDate, rates: List<ExchangeRateSnapshot>): String {
        if (rates.isEmpty()) {
            return emptyRatesOnDateText(requestedDate)
        }
        val cbrDateHeader = rates.firstOrNull()?.cbrDate?.takeIf { it.isNotBlank() }
        val header = buildString {
            append("Курсы на дату запроса $requestedDate (запрос к API ЦБ при вызове инструмента, не из локальной БД).\n")
            if (cbrDateHeader != null) {
                append("Дата в ответе ЦБ (атрибут Date в XML): $cbrDateHeader\n")
            }
        }
        val lines = rates.joinToString("\n") {
            "${it.charCode}: ${it.valuePerUnit} RUB за ${it.nominal} ед., дата ЦБ ${it.cbrDate}, загружено (epoch ms)=${it.fetchedAtMillis}"
        }
        return header + lines
    }

    fun emptyRatesOnDateText(requestedDate: LocalDate): String =
        "Не удалось получить курсы на дату $requestedDate: пустой ответ ЦБ, нет данных в XML после фильтра по кодам валют или ошибка сети/парсинга. Укажите коды как в XML ЦБ (USD, EUR, …), проверьте доступ к https://www.cbr.ru/scripts/XML_daily.asp и что дата не в будущем. В выходные и праздники ЦБ может вернуть курс на ближайший рабочий день — смотрите поле «дата ЦБ» в ответе при успешной загрузке."

    fun formatDynamicQuotesText(
        requestedFrom: LocalDate,
        requestedTo: LocalDate,
        series: CbrDynamicSeries?,
    ): String {
        if (series == null) {
            return """Ответ ЦБ не распознан как XML динамики курса (нет корневого ValCurs или отказ сервиса). Проверьте val_nm_rq и доступ к https://www.cbr.ru/scripts/XML_dynamic.asp (параметры date_req1, date_req2 в формате DD/MM/YYYY, VAL_NM_RQ — код из ежедневного курса). Запрошенный интервал: $requestedFrom … $requestedTo."""
        }
        val meta = buildList {
            add("Динамика официального курса ЦБ (запрос к XML_dynamic.asp при вызове инструмента, не из локальной БД).")
            add("VAL_NM_RQ=${series.valuteId}")
            series.name?.takeIf { it.isNotBlank() }?.let { add("Серия: $it") }
            if (series.dateRange1 != null && series.dateRange2 != null) {
                add("Диапазон в ответе ЦБ: ${series.dateRange1} — ${series.dateRange2}")
            }
            add("Запрошенный интервал: $requestedFrom … $requestedTo")
        }.joinToString("\n")
        if (series.records.isEmpty()) {
            return "$meta\n\nЗаписей Record в XML нет (по выбранным датам данных может не быть — праздники, выходные или некорректный код валюты)."
        }
        val lines = series.records.joinToString("\n") { r ->
            "${r.recordDate}: ${r.valueRub} (руб. за единицу номинала: Value/Nominal по блоку Record)"
        }
        val distinct = series.records.map { it.valueRub }.distinct()
        val flatNote = if (series.records.size > 2 && distinct.size == 1) {
            "\n\nВо всех днях одна и та же цифра: так бывает для выбранной серии ЦБ или при дублирующей разметке в XML; сверяйте с https://www.cbr.ru/currency_base/dynamics/ (код val_nm_rq и период)."
        } else {
            ""
        }
        return "$meta\n\n$lines$flatNote"
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
