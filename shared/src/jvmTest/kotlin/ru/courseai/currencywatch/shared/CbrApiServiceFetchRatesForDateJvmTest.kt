package ru.courseai.currencywatch.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CbrApiServiceFetchRatesForDateJvmTest {

    @Test
    fun fetchRatesForDateSendsDateReqAndParsesUsd() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.toString().contains("/scripts/XML_daily.asp"))
            assertEquals("01/06/2024", request.url.parameters["date_req"])
            respond(
                content = SAMPLE_DAILY_XML,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/xml"),
            )
        }
        val client = HttpClient(engine)
        val api = CbrApiService(client)
        val rows = api.fetchRatesForDate(LocalDate(2024, 6, 1))
        assertEquals(1, rows.size)
        val usd = rows.single()
        assertEquals("USD", usd.charCode)
        assertEquals(1, usd.nominal)
        assertEquals(90.0, usd.valuePerUnit)
        assertEquals("01.06.2024", usd.cbrDate)
    }

    companion object {
        private val SAMPLE_DAILY_XML = """
            <?xml version="1.0" encoding="windows-1251"?>
            <ValCurs Date="01.06.2024">
            <Valute ID="R01235">
            <CharCode>USD</CharCode>
            <Nominal>1</Nominal>
            <Value>90,0000</Value>
            </Valute>
            </ValCurs>
        """.trimIndent().encodeToByteArray()
    }
}
