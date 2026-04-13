package ru.courseai.currencywatch.shared

import kotlinx.coroutines.test.runTest
import ru.courseai.currencywatch.shared.model.ExchangeRateRow
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeRateRepositoryTest {

    @Test
    fun syncAndStoreInsertsWhenNonEmpty() = runTest {
        val db = RecordingDatabaseHelper()
        var fetched: List<ExchangeRateRow>? = null
        val source = CbrRatesSource {
            fetched = listOf(row("USD", 1, 1.0, "d", 1L))
            fetched!!
        }
        val repo = ExchangeRateRepository(db, source)
        repo.syncAndStore()
        assertEquals(1, db.inserted.size)
        assertEquals("USD", db.inserted.single().charCode)
    }

    @Test
    fun syncAndStoreSkipsInsertWhenEmpty() = runTest {
        val db = RecordingDatabaseHelper()
        val source = CbrRatesSource { emptyList() }
        ExchangeRateRepository(db, source).syncAndStore()
        assertTrue(db.inserted.isEmpty())
    }

    @Test
    fun getSummaryPassesWindowOfHoursToDatabase() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(
                snap("USD", 10.0, 1_000L),
            )
        }
        val repo = ExchangeRateRepository(db, CbrRatesSource { emptyList() })
        repo.getSummary(hours = 48, currencies = null)
        assertEquals(48L * 3_600_000L, db.lastToMillis - db.lastFromMillis)
    }

    @Test
    fun getSummaryFiltersByCurrencySet() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(
                snap("USD", 1.0, 1L),
                snap("EUR", 2.0, 2L),
            )
        }
        val repo = ExchangeRateRepository(db, CbrRatesSource { emptyList() })
        val summaries = repo.getSummary(24, setOf("EUR"))
        assertEquals(1, summaries.size)
        assertEquals("EUR", summaries.single().charCode)
    }

    @Test
    fun getSummaryNullOrEmptyCurrencySetMeansNoFilter() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(snap("USD", 1.0, 1L), snap("EUR", 2.0, 2L))
        }
        val repo = ExchangeRateRepository(db, CbrRatesSource { emptyList() })
        assertEquals(2, repo.getSummary(24, null).size)
        assertEquals(2, repo.getSummary(24, emptySet()).size)
    }

    private fun row(
        code: String,
        nominal: Int,
        v: Double,
        date: String,
        fetched: Long,
    ) = ExchangeRateRow(code, nominal, v, date, fetched)

    private fun snap(code: String, v: Double, fetched: Long) =
        ExchangeRateSnapshot(code, 1, v, "d", fetched)

    private class RecordingDatabaseHelper : DatabaseHelper {
        val inserted = mutableListOf<ExchangeRateRow>()
        var snapshotsToReturn: List<ExchangeRateSnapshot> = emptyList()
        var lastFromMillis: Long = -1L
        var lastToMillis: Long = -1L

        override suspend fun insertSnapshots(rows: List<ExchangeRateRow>) {
            inserted.addAll(rows)
        }

        override suspend fun selectSnapshots(fromMillis: Long, toMillis: Long): List<ExchangeRateSnapshot> {
            lastFromMillis = fromMillis
            lastToMillis = toMillis
            return snapshotsToReturn
        }
    }
}
