package ru.courseai.currencywatch.shared

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CbrApiServiceFormatTest {

    @Test
    fun formatDateReqForCbrPadsDayAndMonth() {
        assertEquals("01/06/2024", CbrApiService.formatDateReqForCbr(LocalDate(2024, 6, 1)))
        assertEquals("15/12/1999", CbrApiService.formatDateReqForCbr(LocalDate(1999, 12, 15)))
    }
}
