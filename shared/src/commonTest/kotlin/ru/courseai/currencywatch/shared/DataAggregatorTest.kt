package ru.courseai.currencywatch.shared

import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataAggregatorTest {

    @Test
    fun emptyListReturnsEmpty() {
        assertTrue(DataAggregator.aggregate(emptyList()).isEmpty())
    }

    @Test
    fun singleSampleComputesAvgMinMaxAndZeroChange() {
        val rows = listOf(snap("USD", 100.0, 1L))
        val s = DataAggregator.aggregate(rows).single()
        assertEquals("USD", s.charCode)
        assertEquals(100.0, s.avg, 1e-9)
        assertEquals(100.0, s.min, 1e-9)
        assertEquals(100.0, s.max, 1e-9)
        assertEquals(0.0, s.change, 1e-9)
        assertEquals(1, s.sampleCount)
    }

    @Test
    fun multipleSamplesSameCurrencySortedByTime() {
        val rows = listOf(
            snap("EUR", 90.0, 100L),
            snap("EUR", 92.0, 300L),
            snap("EUR", 91.0, 200L),
        )
        val s = DataAggregator.aggregate(rows).single()
        assertEquals(91.0, s.avg, 1e-9)
        assertEquals(90.0, s.min, 1e-9)
        assertEquals(92.0, s.max, 1e-9)
        assertEquals(2.0, s.change, 1e-9)
        assertEquals(3, s.sampleCount)
    }

    @Test
    fun changeIsLastMinusFirstInTimeOrder() {
        val rows = listOf(
            snap("GBP", 10.0, 10L),
            snap("GBP", 7.0, 20L),
        )
        val s = DataAggregator.aggregate(rows).single()
        assertEquals(-3.0, s.change, 1e-9)
    }

    @Test
    fun multipleCurrenciesSortedByCode() {
        val rows = listOf(
            snap("ZZZ", 1.0, 1L),
            snap("AAA", 2.0, 1L),
            snap("MMM", 3.0, 1L),
        )
        val codes = DataAggregator.aggregate(rows).map { it.charCode }
        assertEquals(listOf("AAA", "MMM", "ZZZ"), codes)
    }

    @Test
    fun singleCurrencyUnsortedInputStillSortedByFetchedTime() {
        val rows = listOf(
            snap("CAD", 3.0, 50L),
            snap("CAD", 1.0, 10L),
            snap("CAD", 2.0, 30L),
        )
        val s = DataAggregator.aggregate(rows).single()
        assertEquals(2.0, s.change, 1e-9)
        assertEquals(3, s.sampleCount)
    }

    @Test
    fun duplicateTimestampsStillAggregated() {
        val rows = listOf(
            snap("X", 5.0, 1L),
            snap("X", 15.0, 1L),
        )
        val s = DataAggregator.aggregate(rows).single()
        assertEquals(10.0, s.avg, 1e-9)
        assertEquals(5.0, s.min, 1e-9)
        assertEquals(15.0, s.max, 1e-9)
    }

    private fun snap(code: String, value: Double, fetched: Long) =
        ExchangeRateSnapshot(
            charCode = code,
            nominal = 1,
            valuePerUnit = value,
            cbrDate = "d",
            fetchedAtMillis = fetched,
        )
}
