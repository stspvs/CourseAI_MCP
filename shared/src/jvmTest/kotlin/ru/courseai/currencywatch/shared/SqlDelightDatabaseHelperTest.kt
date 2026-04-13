package ru.courseai.currencywatch.shared

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import ru.courseai.currencywatch.shared.model.ExchangeRateRow

class SqlDelightDatabaseHelperTest {

    private val tempFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    @Test
    fun insertAndSelectByTimeRange() = runTest {
        val dbFile = createTempDbFile()
        val helper = createJvmDatabaseHelper(dbFile)

        val t0 = 1_000_000L
        helper.insertSnapshots(
            listOf(
                row("USD", 90.5, t0),
                row("EUR", 100.0, t0 + 1),
            ),
        )

        val inWindow = helper.selectSnapshots(fromMillis = t0 - 1, toMillis = t0 + 2)
        assertEquals(2, inWindow.size)
        assertEquals(listOf("USD", "EUR"), inWindow.map { it.charCode })

        val empty = helper.selectSnapshots(fromMillis = t0 + 100, toMillis = t0 + 200)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun selectBoundaryInclusive() = runTest {
        val dbFile = createTempDbFile()
        val helper = createJvmDatabaseHelper(dbFile)
        val t = 5_000L
        helper.insertSnapshots(listOf(row("X", 1.0, t)))

        assertEquals(1, helper.selectSnapshots(t, t).size)
        assertEquals(0, helper.selectSnapshots(t + 1, t + 2).size)
    }

    @Test
    fun maxFetchedAtMillisNullWhenEmpty() = runTest {
        val helper = createJvmDatabaseHelper(createTempDbFile())
        assertEquals(null, helper.maxFetchedAtMillis())
    }

    @Test
    fun maxFetchedAtMillisAfterInsert() = runTest {
        val helper = createJvmDatabaseHelper(createTempDbFile())
        val t = 9_999_888L
        helper.insertSnapshots(listOf(row("USD", 1.0, t), row("EUR", 2.0, t - 1)))
        assertEquals(t, helper.maxFetchedAtMillis())
    }

    @Test
    fun selectLatestPerCurrencyPicksMaxFetched() = runTest {
        val helper = createJvmDatabaseHelper(createTempDbFile())
        helper.insertSnapshots(
            listOf(
                row("USD", 10.0, 100L),
                row("USD", 11.0, 300L),
                row("USD", 12.0, 200L),
            ),
        )
        val latest = helper.selectLatestPerCurrency().single()
        assertEquals("USD", latest.charCode)
        assertEquals(11.0, latest.valuePerUnit, 1e-9)
        assertEquals(300L, latest.fetchedAtMillis)
    }

    @Test
    fun selectLatestPerCurrencyMultipleCodes() = runTest {
        val helper = createJvmDatabaseHelper(createTempDbFile())
        helper.insertSnapshots(
            listOf(
                row("AAA", 1.0, 10L),
                row("BBB", 2.0, 20L),
            ),
        )
        val codes = helper.selectLatestPerCurrency().map { it.charCode }.sorted()
        assertEquals(listOf("AAA", "BBB"), codes)
    }

    private fun createTempDbFile(): File {
        val f = File.createTempFile("currency-test-", ".db")
        tempFiles.add(f)
        return f
    }

    private fun row(code: String, v: Double, fetched: Long) =
        ExchangeRateRow(code, 1, v, "01.01.2024", fetched)
}
