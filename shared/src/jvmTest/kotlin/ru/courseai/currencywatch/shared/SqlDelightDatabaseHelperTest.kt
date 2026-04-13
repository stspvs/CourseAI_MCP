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

    private fun createTempDbFile(): File {
        val f = File.createTempFile("currency-test-", ".db")
        tempFiles.add(f)
        return f
    }

    private fun row(code: String, v: Double, fetched: Long) =
        ExchangeRateRow(code, 1, v, "01.01.2024", fetched)
}
