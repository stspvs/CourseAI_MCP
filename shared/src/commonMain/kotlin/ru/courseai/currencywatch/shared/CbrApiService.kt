package ru.courseai.currencywatch.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import ru.courseai.currencywatch.shared.model.ExchangeRateRow

interface CbrRatesSource {
    suspend fun fetchTodayRates(): List<ExchangeRateRow>

    suspend fun fetchRatesForDate(date: LocalDate): List<ExchangeRateRow> {
        return emptyList()
    }
}

class CbrApiService(
    private val http: HttpClient,
) : CbrRatesSource {
    override suspend fun fetchTodayRates(): List<ExchangeRateRow> {
        val response: HttpResponse = http.get(CBR_DAILY_URL)
        val bytes: ByteArray = response.body()
        val xml = decodeCbrXmlBytes(bytes)
        val now = Clock.System.now().toEpochMilliseconds()
        return CbrXmlParser.parseDailyXml(xml, now)
    }

    override suspend fun fetchRatesForDate(date: LocalDate): List<ExchangeRateRow> {
        val response: HttpResponse = http.get(CBR_DAILY_URL) {
            parameter("date_req", formatDateReqForCbr(date))
        }
        val bytes: ByteArray = response.body()
        val xml = decodeCbrXmlBytes(bytes)
        val now = Clock.System.now().toEpochMilliseconds()
        return CbrXmlParser.parseDailyXml(xml, now)
    }

    companion object {
        const val CBR_DAILY_URL = "https://www.cbr.ru/scripts/XML_daily.asp"

        /** Параметр `date_req` для XML_daily.asp: DD/MM/YYYY. */
        internal fun formatDateReqForCbr(date: LocalDate): String {
            val d = date.dayOfMonth.toString().padStart(2, '0')
            val m = date.monthNumber.toString().padStart(2, '0')
            return "$d/$m/${date.year}"
        }
    }
}

expect fun decodeCbrXmlBytes(bytes: ByteArray): String
