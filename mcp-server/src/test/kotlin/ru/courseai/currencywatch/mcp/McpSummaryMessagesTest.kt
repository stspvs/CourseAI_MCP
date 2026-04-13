package ru.courseai.currencywatch.mcp

import ru.courseai.currencywatch.shared.model.CurrencySummary
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpSummaryMessagesTest {

    @Test
    fun formatLatestRatesTextEmpty() {
        val t = McpSummaryMessages.formatLatestRatesText(emptyList())
        assertTrue(t.contains("Нет сохранённых курсов"))
    }

    @Test
    fun formatLatestRatesTextOneRow() {
        val t = McpSummaryMessages.formatLatestRatesText(
            listOf(
                ExchangeRateSnapshot("USD", 1, 90.5, "13.04.2026", 1_700L),
            ),
        )
        assertTrue(t.contains("USD"))
        assertTrue(t.contains("90.5"))
        assertTrue(t.contains("13.04.2026"))
    }

    @Test
    fun formatSummaryTextNonEmpty() {
        val t = McpSummaryMessages.formatSummaryText(
            listOf(
                CurrencySummary("EUR", 100.0, 99.0, 101.0, 1.0, 2),
            ),
            hours = 24,
        )
        assertTrue(t.startsWith("Период: 24 ч."))
        assertTrue(t.contains("EUR"))
        assertTrue(t.contains("avg="))
    }

    @Test
    fun requestedCurrenciesDisplayTrimsAndUppercases() {
        assertEquals("USD, EUR", McpSummaryMessages.requestedCurrenciesDisplay(setOf(" usd ", "eur")))
    }

    @Test
    fun requestedCurrenciesDisplayEmpty() {
        assertEquals("", McpSummaryMessages.requestedCurrenciesDisplay(null))
        assertEquals("", McpSummaryMessages.requestedCurrenciesDisplay(emptySet()))
    }

    @Test
    fun emptySummaryCurrencyMismatchListsCodes() {
        val msg = McpSummaryMessages.emptySummaryCurrencyMismatch(
            hours = 24,
            totalInWindow = 100,
            requestedCurrencies = setOf("XXX"),
            availableCodes = listOf("USD", "EUR"),
        )
        assertTrue(msg.contains("[XXX]"))
        assertTrue(msg.contains("100 строк"))
        assertTrue(msg.contains("USD, EUR"))
        assertTrue(msg.contains("без параметра currencies"))
    }

    @Test
    fun emptySummaryStaleWindow() {
        val msg = McpSummaryMessages.emptySummaryStaleWindow(hours = 24, ageHours = 48L)
        assertTrue(msg.contains("48"))
        assertTrue(msg.contains("168"))
    }

    @Test
    fun emptySummaryNoRowsInDb() {
        val msg = McpSummaryMessages.emptySummaryNoRowsInDb(24)
        assertTrue(msg.contains("XML_daily.asp"))
    }
}
