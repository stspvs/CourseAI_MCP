package ru.courseai.currencywatch.mcp

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class McpDynamicQuotesArgsTest {

    @Test
    fun successWithIsoDates() {
        val o = buildJsonObject {
            put("val_nm_rq", "r01235")
            put("date_from", "2001-03-02")
            put("date_to", "2001-03-14")
        }
        val r = parseDynamicQuotesArgs(o)
        assertIs<ParseDynamicQuotesResult.Success>(r)
        assertEquals("R01235", r.valuteId)
        assertEquals(LocalDate(2001, 3, 2), r.dateFrom)
        assertEquals(LocalDate(2001, 3, 14), r.dateTo)
    }

    @Test
    fun failureWhenFromAfterTo() {
        val o = buildJsonObject {
            put("val_nm_rq", "R01235")
            put("date_from", "2001-03-14")
            put("date_to", "2001-03-02")
        }
        val r = parseDynamicQuotesArgs(o)
        assertIs<ParseDynamicQuotesResult.Failure>(r)
        assertTrue(r.message.contains("date_from"))
    }

    @Test
    fun failureInvalidValuteId() {
        val o = buildJsonObject {
            put("val_nm_rq", "USD")
            put("date_from", "2001-03-02")
            put("date_to", "2001-03-14")
        }
        val r = parseDynamicQuotesArgs(o)
        assertIs<ParseDynamicQuotesResult.Failure>(r)
    }
}
