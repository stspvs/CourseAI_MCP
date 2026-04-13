package ru.courseai.currencywatch.shared

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.courseai.currencywatch.shared.model.ExchangeRateRow
import ru.courseai.currencywatch.shared.model.ExchangeRateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeRateRepositoryTest {

    @Test
    fun syncAndStoreInsertsWhenNonEmpty() = runTest {
        val db = RecordingDatabaseHelper()
        val source = cbrSource(
            fetchToday = {
                listOf(row("USD", 1, 1.0, "d", 1L))
            },
        )
        val repo = ExchangeRateRepository(db, source)
        assertEquals(1, repo.syncAndStore())
        assertEquals(1, db.inserted.size)
        assertEquals("USD", db.inserted.single().charCode)
    }

    @Test
    fun syncAndStoreSkipsInsertWhenEmpty() = runTest {
        val db = RecordingDatabaseHelper()
        val source = cbrSource()
        assertEquals(0, ExchangeRateRepository(db, source).syncAndStore())
        assertTrue(db.inserted.isEmpty())
    }

    @Test
    fun getSummaryPassesWindowOfHoursToDatabase() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(
                snap("USD", 10.0, 1_000L),
            )
        }
        val repo = ExchangeRateRepository(db, cbrSource())
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
        val repo = ExchangeRateRepository(db, cbrSource())
        val summaries = repo.getSummary(24, setOf("EUR"))
        assertEquals(1, summaries.size)
        assertEquals("EUR", summaries.single().charCode)
    }

    @Test
    fun distinctCurrencyCodesInWindowSortedUnique() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(
                snap("USD", 1.0, 3L),
                snap("EUR", 2.0, 2L),
                snap("USD", 1.1, 1L),
            )
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        assertEquals(listOf("EUR", "USD"), repo.distinctCurrencyCodesInWindow(24))
    }

    @Test
    fun getSummaryNormalizesCurrencyCodesToUppercase() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(snap("EUR", 2.0, 2L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        val summaries = repo.getSummary(24, setOf("eur"))
        assertEquals(1, summaries.size)
        assertEquals("EUR", summaries.single().charCode)
    }

    @Test
    fun getSummaryNullOrEmptyCurrencySetMeansNoFilter() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(snap("USD", 1.0, 1L), snap("EUR", 2.0, 2L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
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

    @Test
    fun getLatestRatesFiltersByCurrencySet() = runTest {
        val db = RecordingDatabaseHelper().apply {
            latestPerCurrency = listOf(
                snap("USD", 1.0, 1L),
                snap("EUR", 2.0, 2L),
            )
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        val latest = repo.getLatestRates(setOf("EUR"))
        assertEquals(1, latest.size)
        assertEquals("EUR", latest.single().charCode)
    }

    @Test
    fun getLatestRatesNormalizesCurrencyCodesToUppercase() = runTest {
        val db = RecordingDatabaseHelper().apply {
            latestPerCurrency = listOf(snap("EUR", 2.0, 2L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        val latest = repo.getLatestRates(setOf("eur"))
        assertEquals(1, latest.size)
        assertEquals("EUR", latest.single().charCode)
    }

    @Test
    fun getLatestRatesEmptySetReturnsNothing() = runTest {
        val db = RecordingDatabaseHelper().apply {
            latestPerCurrency = listOf(snap("USD", 1.0, 1L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        assertTrue(repo.getLatestRates(emptySet()).isEmpty())
    }

    @Test
    fun getSummaryEmptyWhenFilterDoesNotMatchAnyCode() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(snap("USD", 1.0, 1L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        assertTrue(repo.getSummary(24, setOf("XXX")).isEmpty())
    }

    @Test
    fun getLatestRatesReturnsEmptyWhenCodeNotInDb() = runTest {
        val db = RecordingDatabaseHelper().apply {
            latestPerCurrency = listOf(snap("USD", 1.0, 1L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        assertTrue(repo.getLatestRates(setOf("EUR")).isEmpty())
    }

    @Test
    fun getLatestRatesTrimsAndUppercasesCodes() = runTest {
        val db = RecordingDatabaseHelper().apply {
            latestPerCurrency = listOf(snap("USD", 5.0, 1L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        val r = repo.getLatestRates(setOf(" usd ")).single()
        assertEquals("USD", r.charCode)
    }

    @Test
    fun snapshotCountInWindow() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(snap("A", 1.0, 1L), snap("B", 2.0, 2L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        assertEquals(2, repo.snapshotCountInWindow(24))
    }

    @Test
    fun distinctCurrencyCodesInWindowEmpty() = runTest {
        val db = RecordingDatabaseHelper()
        val repo = ExchangeRateRepository(db, cbrSource())
        assertTrue(repo.distinctCurrencyCodesInWindow(24).isEmpty())
    }

    @Test
    fun getMaxFetchedAtMillisDelegatesToDatabase() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(snap("USD", 1.0, 500L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        assertEquals(500L, repo.getMaxFetchedAtMillis())
    }

    @Test
    fun syncAndStoreReturnsMultipleRowCount() = runTest {
        val db = RecordingDatabaseHelper()
        val source = cbrSource(
            fetchToday = {
                listOf(row("A", 1, 1.0, "d", 1L), row("B", 1, 2.0, "d", 2L))
            },
        )
        assertEquals(2, ExchangeRateRepository(db, source).syncAndStore())
        assertEquals(2, db.inserted.size)
    }

    @Test
    fun getSummaryWhitespaceOnlyCurrencySetActsAsNoFilter() = runTest {
        val db = RecordingDatabaseHelper().apply {
            snapshotsToReturn = listOf(snap("USD", 1.0, 1L), snap("EUR", 2.0, 2L))
        }
        val repo = ExchangeRateRepository(db, cbrSource())
        assertEquals(2, repo.getSummary(24, setOf("   ", " ")).size)
    }

    @Test
    fun getRatesForDateFiltersByCurrency() = runTest {
        val db = RecordingDatabaseHelper()
        val d = LocalDate(2024, 6, 1)
        val source = cbrSource(
            fetchDate = { date ->
                assertEquals(d, date)
                listOf(
                    row("USD", 1, 90.0, "01.06.2024", 1L),
                    row("EUR", 1, 100.0, "01.06.2024", 1L),
                )
            },
        )
        val repo = ExchangeRateRepository(db, source)
        val rates = repo.getRatesForDate(setOf("USD"), d)
        assertEquals(1, rates.size)
        assertEquals("USD", rates.single().charCode)
        assertEquals(90.0, rates.single().valuePerUnit)
    }

    @Test
    fun getRatesForDateNormalizesCurrencyCodes() = runTest {
        val db = RecordingDatabaseHelper()
        val d = LocalDate(2024, 1, 15)
        val source = cbrSource(
            fetchDate = {
                listOf(row("EUR", 1, 50.0, "15.01.2024", 99L))
            },
        )
        val repo = ExchangeRateRepository(db, source)
        val rates = repo.getRatesForDate(setOf("eur"), d)
        assertEquals("EUR", rates.single().charCode)
    }

    @Test
    fun getRatesForDateEmptyCurrenciesReturnsEmpty() = runTest {
        val db = RecordingDatabaseHelper()
        val source = cbrSource(
            fetchDate = { listOf(row("USD", 1, 1.0, "d", 1L)) },
        )
        val repo = ExchangeRateRepository(db, source)
        assertTrue(repo.getRatesForDate(emptySet(), LocalDate(2024, 1, 1)).isEmpty())
    }

    @Test
    fun getRatesForDateReturnsEmptyWhenSourceEmpty() = runTest {
        val db = RecordingDatabaseHelper()
        val repo = ExchangeRateRepository(db, cbrSource())
        assertTrue(repo.getRatesForDate(setOf("USD"), LocalDate(2024, 1, 1)).isEmpty())
    }

    @Test
    fun getRatesForDateReturnsEmptyWhenNoMatchingCurrencyAfterFilter() = runTest {
        val db = RecordingDatabaseHelper()
        val source = cbrSource(
            fetchDate = {
                listOf(row("USD", 1, 90.0, "01.06.2024", 1L))
            },
        )
        val repo = ExchangeRateRepository(db, source)
        assertTrue(repo.getRatesForDate(setOf("XXX"), LocalDate(2024, 6, 1)).isEmpty())
    }

    private fun cbrSource(
        fetchToday: suspend () -> List<ExchangeRateRow> = { emptyList() },
        fetchDate: suspend (LocalDate) -> List<ExchangeRateRow> = { emptyList() },
    ): CbrRatesSource =
        object : CbrRatesSource {
            override suspend fun fetchTodayRates(): List<ExchangeRateRow> = fetchToday()

            override suspend fun fetchRatesForDate(date: LocalDate): List<ExchangeRateRow> = fetchDate(date)
        }

    private class RecordingDatabaseHelper : DatabaseHelper {
        val inserted = mutableListOf<ExchangeRateRow>()
        var snapshotsToReturn: List<ExchangeRateSnapshot> = emptyList()
        var latestPerCurrency: List<ExchangeRateSnapshot> = emptyList()
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

        override suspend fun selectLatestPerCurrency(): List<ExchangeRateSnapshot> = latestPerCurrency

        override suspend fun maxFetchedAtMillis(): Long? {
            val fromSnapshots = snapshotsToReturn.maxOfOrNull { it.fetchedAtMillis }
            val fromLatest = latestPerCurrency.maxOfOrNull { it.fetchedAtMillis }
            val fromInserted = inserted.maxOfOrNull { it.fetchedAtMillis }
            return listOfNotNull(fromSnapshots, fromLatest, fromInserted).maxOrNull()
        }
    }
}
