package ru.courseai.currencywatch.shared.model

import kotlinx.serialization.Serializable

data class ExchangeRateRow(
    val charCode: String,
    val nominal: Int,
    val valuePerUnit: Double,
    val cbrDate: String,
    val fetchedAtMillis: Long,
)

@Serializable
data class ExchangeRateSnapshot(
    val charCode: String,
    val nominal: Int,
    val valuePerUnit: Double,
    val cbrDate: String,
    val fetchedAtMillis: Long,
)

@Serializable
data class CurrencySummary(
    val charCode: String,
    val avg: Double,
    val min: Double,
    val max: Double,
    val change: Double,
    val sampleCount: Int,
)
