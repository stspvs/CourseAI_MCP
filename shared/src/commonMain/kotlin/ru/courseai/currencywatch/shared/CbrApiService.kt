package ru.courseai.currencywatch.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.datetime.Clock
import ru.courseai.currencywatch.shared.model.ExchangeRateRow

fun interface CbrRatesSource {
    suspend fun fetchTodayRates(): List<ExchangeRateRow>
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

    companion object {
        const val CBR_DAILY_URL = "https://www.cbr.ru/scripts/XML_daily.asp"
    }
}

expect fun decodeCbrXmlBytes(bytes: ByteArray): String
