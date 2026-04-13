package ru.courseai.currencywatch.shared

import kotlinx.datetime.Clock
import ru.courseai.currencywatch.shared.model.CurrencySummary

class ExchangeRateRepository(
    private val databaseHelper: DatabaseHelper,
    private val cbrRatesSource: CbrRatesSource,
) {
    suspend fun syncAndStore() {
        val rows = cbrRatesSource.fetchTodayRates()
        if (rows.isNotEmpty()) {
            databaseHelper.insertSnapshots(rows)
        }
    }

    suspend fun getSummary(hours: Int, currencies: Set<String>?): List<CurrencySummary> {
        val to = Clock.System.now().toEpochMilliseconds()
        val from = to - hours.toLong() * 3_600_000L
        val rows = databaseHelper.selectSnapshots(from, to)
        val filtered = when {
            currencies.isNullOrEmpty() -> rows
            else -> rows.filter { it.charCode in currencies }
        }
        return DataAggregator.aggregate(filtered)
    }
}
