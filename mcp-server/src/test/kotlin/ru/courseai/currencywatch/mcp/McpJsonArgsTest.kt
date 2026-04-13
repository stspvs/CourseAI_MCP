package ru.courseai.currencywatch.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpJsonArgsTest {

    @Test
    fun toCurrencyCodeStringSetNullWhenEmpty() {
        assertNull(JsonArray(emptyList()).toCurrencyCodeStringSet())
    }

    @Test
    fun toCurrencyCodeStringSetCollectsPrimitives() {
        val arr = JsonArray(listOf(JsonPrimitive("USD"), JsonPrimitive("EUR")))
        assertEquals(setOf("USD", "EUR"), arr.toCurrencyCodeStringSet())
    }

}
