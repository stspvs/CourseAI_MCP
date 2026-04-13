package ru.courseai.currencywatch.shared

import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataAggregatorStressTest {

    @Test
    fun manyCurrenciesManySamples() {
        val rows = ArrayList<ExchangeRateSnapshot>(50_000)
        var t = 0L
        repeat(100) { code ->
            repeat(500) { i ->
                rows.add(
                    ExchangeRateSnapshot(
                        charCode = "C$code",
                        nominal = 1,
                        valuePerUnit = code * 0.01 + i * 0.0001,
                        cbrDate = "d",
                        fetchedAtMillis = t++,
                    ),
                )
            }
        }
        val summaries = DataAggregator.aggregate(rows)
        assertEquals(100, summaries.size)
        assertTrue(summaries.all { it.sampleCount == 500 })
        assertTrue(summaries.first().charCode.startsWith("C"))
    }

    @Test
    fun longMonotonicSeriesChangeMatchesEndpoints() {
        val n = 10_000
        val rows = List(n) { i ->
            ExchangeRateSnapshot(
                charCode = "USD",
                nominal = 1,
                valuePerUnit = i.toDouble(),
                cbrDate = "d",
                fetchedAtMillis = i.toLong(),
            )
        }
        val s = DataAggregator.aggregate(rows).single()
        assertEquals(n - 1.0, s.change, 1e-6)
        assertEquals(0.0, s.min, 1e-9)
        assertEquals((n - 1).toDouble(), s.max, 1e-6)
    }
}
