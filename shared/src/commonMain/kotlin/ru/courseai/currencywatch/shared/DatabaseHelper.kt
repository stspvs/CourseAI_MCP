package ru.courseai.currencywatch.shared

import ru.courseai.currencywatch.shared.model.ExchangeRateRow
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot

interface DatabaseHelper {
    suspend fun insertSnapshots(rows: List<ExchangeRateRow>)
    suspend fun selectSnapshots(fromMillis: Long, toMillis: Long): List<ExchangeRateSnapshot>
    suspend fun selectLatestPerCurrency(): List<ExchangeRateSnapshot>
    suspend fun maxFetchedAtMillis(): Long?
}
