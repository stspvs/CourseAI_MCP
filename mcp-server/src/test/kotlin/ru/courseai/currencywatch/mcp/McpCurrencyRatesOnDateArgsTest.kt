package ru.courseai.currencywatch.mcp

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class McpCurrencyRatesOnDateArgsTest {

    @Test
    fun failureWhenArgumentsNull() {
        val r = parseCurrencyRatesOnDateArgs(null)
        assertIs<ParseCurrencyRatesOnDateResult.Failure>(r)
        assertTrue(r.message.contains("currencies"))
    }

    @Test
    fun failureWhenCurrenciesMissing() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                put("date", "2024-06-01")
            },
        )
        assertIs<ParseCurrencyRatesOnDateResult.Failure>(r)
        assertTrue(r.message.contains("currencies"))
    }

    @Test
    fun failureWhenCurrenciesEmptyArray() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") { }
                put("date", "2024-06-01")
            },
        )
        assertIs<ParseCurrencyRatesOnDateResult.Failure>(r)
    }

    @Test
    fun failureWhenDateMissing() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("USD"))
                }
            },
        )
        assertIs<ParseCurrencyRatesOnDateResult.Failure>(r)
        assertTrue(r.message.contains("date"))
    }

    @Test
    fun failureWhenDateBlank() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("USD"))
                }
                put("date", "   ")
            },
        )
        assertIs<ParseCurrencyRatesOnDateResult.Failure>(r)
        assertTrue(r.message.contains("date"))
    }

    @Test
    fun failureWhenDateInvalid() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("USD"))
                }
                put("date", "not-a-date")
            },
        )
        assertIs<ParseCurrencyRatesOnDateResult.Failure>(r)
        assertTrue(r.message.contains("not-a-date"))
        assertTrue(r.message.contains("Некорректная дата"))
    }

    @Test
    fun successTrimsDateWhitespace() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("USD"))
                    add(JsonPrimitive("EUR"))
                }
                put("date", "  2024-06-01  ")
            },
        )
        val ok = assertIs<ParseCurrencyRatesOnDateResult.Success>(r)
        assertEquals(setOf("USD", "EUR"), ok.currencies)
        assertEquals(LocalDate(2024, 6, 1), ok.date)
    }

    @Test
    fun successSingleCurrency() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("CNY"))
                }
                put("date", "1999-12-31")
            },
        )
        val ok = assertIs<ParseCurrencyRatesOnDateResult.Success>(r)
        assertEquals(setOf("CNY"), ok.currencies)
        assertEquals(LocalDate(1999, 12, 31), ok.date)
    }

    @Test
    fun successWhenDateIsJsonNumber() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("USD"))
                }
                put("date", JsonPrimitive(20_240_601))
            },
        )
        val ok = assertIs<ParseCurrencyRatesOnDateResult.Success>(r)
        assertEquals(LocalDate(2024, 6, 1), ok.date)
    }

    @Test
    fun successWhenDateIsDdMmYyyy() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("USD"))
                }
                put("date", "15.01.2024")
            },
        )
        val ok = assertIs<ParseCurrencyRatesOnDateResult.Success>(r)
        assertEquals(LocalDate(2024, 1, 15), ok.date)
    }

    @Test
    fun successWhenDateIsCompactYyyymmdd() {
        val r = parseCurrencyRatesOnDateArgs(
            buildJsonObject {
                putJsonArray("currencies") {
                    add(JsonPrimitive("USD"))
                }
                put("date", "20240601")
            },
        )
        val ok = assertIs<ParseCurrencyRatesOnDateResult.Success>(r)
        assertEquals(LocalDate(2024, 6, 1), ok.date)
    }

    @Test
    fun parseFlexibleLocalDateUnit() {
        assertEquals(LocalDate(2024, 3, 9), parseFlexibleLocalDate("2024-03-09"))
        assertEquals(LocalDate(2024, 3, 9), parseFlexibleLocalDate("09.03.2024"))
        assertEquals(LocalDate(2024, 3, 9), parseFlexibleLocalDate("20240309"))
    }
}
