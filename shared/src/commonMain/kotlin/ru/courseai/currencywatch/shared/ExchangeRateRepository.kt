package ru.courseai.currencywatch.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import ru.courseai.currencywatch.shared.model.CbrDynamicSeries
import ru.courseai.currencywatch.shared.model.CurrencySummary
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot

class ExchangeRateRepository(
    private val databaseHelper: DatabaseHelper,
    private val cbrRatesSource: CbrRatesSource,
) {
    /** @return сколько строк курсов сохранено в БД (0 — ЦБ не вернул данные или список пуст). */
    suspend fun syncAndStore(): Int {
        val rows = cbrRatesSource.fetchTodayRates()
        if (rows.isNotEmpty()) {
            databaseHelper.insertSnapshots(rows)
            return rows.size
        }
        return 0
    }

    suspend fun getSummary(hours: Int, currencies: Set<String>?): List<CurrencySummary> {
        val rows = selectSnapshotsInWindow(hours)
        val normalized = normalizeCurrencyFilter(currencies)
        val filtered = when {
            normalized == null -> rows
            else -> rows.filter { it.charCode in normalized }
        }
        return DataAggregator.aggregate(filtered)
    }

    suspend fun snapshotCountInWindow(hours: Int): Int =
        selectSnapshotsInWindow(hours).size

    /** Уникальные `CharCode` из дневного курса ЦБ в окне (как в XML), отсортированы. */
    suspend fun distinctCurrencyCodesInWindow(hours: Int): List<String> =
        selectSnapshotsInWindow(hours).map { it.charCode }.distinct().sorted()

    suspend fun getMaxFetchedAtMillis(): Long? =
        databaseHelper.maxFetchedAtMillis()

    suspend fun getLatestRates(currencies: Set<String>): List<ExchangeRateSnapshot> {
        if (currencies.isEmpty()) return emptyList()
        val normalized = normalizeCurrencyFilter(currencies) ?: return emptyList()
        val rows = databaseHelper.selectLatestPerCurrency()
        return rows.filter { it.charCode in normalized }
    }

    /**
     * Динамика официального курса по коду валюты ЦБ (`VAL_NM_RQ`, например R01235 для USD) за интервал дат.
     * Запрос к API ЦБ при вызове, не из локальной БД.
     */
    suspend fun getDynamicQuotes(valuteId: String, dateFrom: LocalDate, dateTo: LocalDate): CbrDynamicSeries? =
        cbrRatesSource.fetchDynamicQuotes(valuteId, dateFrom, dateTo)

    /** Курсы на календарную дату по данным ЦБ (запрос к API источника, не из локальной БД). */
    suspend fun getRatesForDate(currencies: Set<String>, date: LocalDate): List<ExchangeRateSnapshot> {
        if (currencies.isEmpty()) return emptyList()
        val normalized = normalizeCurrencyFilter(currencies) ?: return emptyList()
        val rows = cbrRatesSource.fetchRatesForDate(date)
        if (rows.isEmpty()) return emptyList()
        return rows
            .filter { it.charCode in normalized }
            .map {
                ExchangeRateSnapshot(
                    charCode = it.charCode,
                    nominal = it.nominal,
                    valuePerUnit = it.valuePerUnit,
                    cbrDate = it.cbrDate,
                    fetchedAtMillis = it.fetchedAtMillis,
                )
            }
    }

    private suspend fun selectSnapshotsInWindow(hours: Int): List<ExchangeRateSnapshot> {
        val to = Clock.System.now().toEpochMilliseconds()
        val from = to - hours.toLong() * 3_600_000L
        return databaseHelper.selectSnapshots(from, to)
    }

    private fun normalizeCurrencyFilter(currencies: Set<String>?): Set<String>? {
        if (currencies.isNullOrEmpty()) return null
        val set = currencies.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        return set.ifEmpty { null }
    }
}
