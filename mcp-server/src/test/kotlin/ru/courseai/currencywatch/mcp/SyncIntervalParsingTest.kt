package ru.courseai.currencywatch.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SyncIntervalParsingTest {

    @Test
    fun parsesPt1h() {
        assertEquals(1.hours, parseAndValidateSyncInterval("PT1H"))
    }

    @Test
    fun parsesPt30m() {
        assertEquals(30.minutes, parseAndValidateSyncInterval("PT30M"))
    }

    @Test
    fun rejectsEmpty() {
        assertFailsWith<IllegalArgumentException> { parseAndValidateSyncInterval("") }
    }

    @Test
    fun rejectsInvalidIso() {
        assertFailsWith<IllegalArgumentException> { parseAndValidateSyncInterval("not-a-duration") }
    }

    @Test
    fun rejectsZeroOrNegative() {
        assertFailsWith<IllegalArgumentException> { parseAndValidateSyncInterval("PT0S") }
        assertFailsWith<IllegalArgumentException> { parseAndValidateSyncInterval("-PT1H") }
    }

    @Test
    fun rejectsUnderOneMinute() {
        assertFailsWith<IllegalArgumentException> { parseAndValidateSyncInterval("PT0.5M") }
        assertFailsWith<IllegalArgumentException> { parseAndValidateSyncInterval("PT30S") }
    }
}
