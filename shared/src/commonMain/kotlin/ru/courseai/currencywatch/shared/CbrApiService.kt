package ru.courseai.currencywatch.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import ru.courseai.currencywatch.shared.model.CbrDynamicSeries
import ru.courseai.currencywatch.shared.model.ExchangeRateRow

interface CbrRatesSource {
    suspend fun fetchTodayRates(): List<ExchangeRateRow>

    suspend fun fetchRatesForDate(date: LocalDate): List<ExchangeRateRow> {
        return emptyList()
    }

    /** Динамика курса (XML_dynamic.asp ЦБ); null, если корневой ValCurs не распознан. */
    suspend fun fetchDynamicQuotes(valuteId: String, dateFrom: LocalDate, dateTo: LocalDate): CbrDynamicSeries? {
        return null
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

    override suspend fun fetchDynamicQuotes(
        valuteId: String,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): CbrDynamicSeries? {
        val response: HttpResponse = http.get(CBR_DYNAMIC_URL) {
            parameter("date_req1", formatDateReqForCbr(dateFrom))
            parameter("date_req2", formatDateReqForCbr(dateTo))
            parameter("VAL_NM_RQ", valuteId.trim())
        }
        val bytes: ByteArray = response.body()
        val xml = decodeCbrXmlBytes(bytes)
        return CbrXmlParser.parseDynamicXml(xml)
    }

    companion object {
        const val CBR_DAILY_URL = "https://www.cbr.ru/scripts/XML_daily.asp"
        const val CBR_DYNAMIC_URL = "https://www.cbr.ru/scripts/XML_dynamic.asp"

        /** Параметр `date_req` для XML_daily.asp: DD/MM/YYYY. */
        internal fun formatDateReqForCbr(date: LocalDate): String {
            val d = date.dayOfMonth.toString().padStart(2, '0')
            val m = date.monthNumber.toString().padStart(2, '0')
            return "$d/$m/${date.year}"
        }
    }
}

expect fun decodeCbrXmlBytes(bytes: ByteArray): String
