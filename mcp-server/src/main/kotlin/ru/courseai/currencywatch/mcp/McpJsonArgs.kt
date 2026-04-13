package ru.courseai.currencywatch.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Разбор аргументов MCP (JSON) в наборы строк для инструментов.
 */
internal fun JsonArray.toCurrencyCodeStringSet(): Set<String>? {
    val set = mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
    return set.ifEmpty { null }
}
