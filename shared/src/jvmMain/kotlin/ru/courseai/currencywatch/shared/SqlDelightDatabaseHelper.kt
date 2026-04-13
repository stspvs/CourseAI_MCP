package ru.courseai.currencywatch.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.courseai.currencywatch.db.CurrencyDatabase
import ru.courseai.currencywatch.db.Exchange_rate_snapshot
import ru.courseai.currencywatch.shared.model.ExchangeRateRow
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot

class SqlDelightDatabaseHelper(
    private val database: CurrencyDatabase,
) : DatabaseHelper {

    override suspend fun insertSnapshots(rows: List<ExchangeRateRow>) = withContext(Dispatchers.IO) {
        database.transaction {
            rows.forEach { row ->
                database.exchangeRatesQueries.insertSnapshot(
                    char_code = row.charCode,
                    nominal = row.nominal.toLong(),
                    value_per_unit = row.valuePerUnit,
                    cbr_date = row.cbrDate,
                    fetched_at = row.fetchedAtMillis,
                )
            }
        }
    }

    override suspend fun selectSnapshots(fromMillis: Long, toMillis: Long): List<ExchangeRateSnapshot> =
        withContext(Dispatchers.IO) {
            database.exchangeRatesQueries.selectByTimeRange(fromMillis, toMillis)
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun selectLatestPerCurrency(): List<ExchangeRateSnapshot> =
        withContext(Dispatchers.IO) {
            database.exchangeRatesQueries.selectLatestPerCurrency()
                .executeAsList()
                .map { row ->
                    ExchangeRateSnapshot(
                        charCode = row.char_code,
                        nominal = row.nominal.toInt(),
                        valuePerUnit = row.value_per_unit,
                        cbrDate = row.cbr_date,
                        fetchedAtMillis = row.fetched_at,
                    )
                }
        }

    override suspend fun maxFetchedAtMillis(): Long? = withContext(Dispatchers.IO) {
        database.exchangeRatesQueries.selectMaxFetchedAt().executeAsOneOrNull()?.max_fetched
    }

    private fun Exchange_rate_snapshot.toDomain(): ExchangeRateSnapshot =
        ExchangeRateSnapshot(
            charCode = char_code,
            nominal = nominal.toInt(),
            valuePerUnit = value_per_unit,
            cbrDate = cbr_date,
            fetchedAtMillis = fetched_at,
        )
}

fun createJvmDatabaseHelper(dbFile: File): DatabaseHelper {
    dbFile.parentFile?.mkdirs()
    val needsCreate = !dbFile.exists() || dbFile.length() == 0L
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (needsCreate) {
        CurrencyDatabase.Schema.create(driver)
    }
    val database = CurrencyDatabase(driver)
    return SqlDelightDatabaseHelper(database)
}
