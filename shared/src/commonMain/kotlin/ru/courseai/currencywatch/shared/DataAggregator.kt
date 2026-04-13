package ru.courseai.currencywatch.shared

import ru.courseai.currencywatch.shared.model.CurrencySummary
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot

object DataAggregator {
    fun aggregate(rows: List<ExchangeRateSnapshot>): List<CurrencySummary> {
        if (rows.isEmpty()) return emptyList()
        return rows
            .groupBy { it.charCode }
            .map { (code, list) ->
                val sorted = list.sortedBy { it.fetchedAtMillis }
                val values = sorted.map { it.valuePerUnit }
                val avg = values.average()
                val min = values.minOrNull() ?: 0.0
                val max = values.maxOrNull() ?: 0.0
                val change = when {
                    values.size >= 2 -> values.last() - values.first()
                    else -> 0.0
                }
                CurrencySummary(
                    charCode = code,
                    avg = avg,
                    min = min,
                    max = max,
                    change = change,
                    sampleCount = values.size,
                )
            }
            .sortedBy { it.charCode }
    }
}
