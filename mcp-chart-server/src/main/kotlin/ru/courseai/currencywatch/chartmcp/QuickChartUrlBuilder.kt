package ru.courseai.currencywatch.chartmcp

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

/**
 * Сборка GET-URL для [QuickChart](https://quickchart.io/documentation/) (`/chart`).
 */
object QuickChartUrlBuilder {

    private const val HOST = "quickchart.io"

    data class Params(
        /** JSON-строка конфигурации Chart.js (поле `chart` в API QuickChart). */
        val chartJson: String,
        val width: Int? = null,
        val height: Int? = null,
        val devicePixelRatio: Double? = null,
        val backgroundColor: String? = null,
        val version: String? = null,
        val format: String? = null,
        val encoding: String? = null,
    )

    fun build(params: Params): String {
        require(params.chartJson.isNotBlank()) { "chart (JSON) не должен быть пустым" }
        return URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = HOST,
            pathSegments = listOf("chart"),
        ).apply {
            with(parameters) {
                append("chart", params.chartJson)
                params.width?.let { append("width", it.toString()) }
                params.height?.let { append("height", it.toString()) }
                params.devicePixelRatio?.let { append("devicePixelRatio", formatNumeric(it)) }
                params.backgroundColor?.let { append("backgroundColor", it) }
                params.version?.let { append("version", it) }
                params.format?.let { append("format", it) }
                params.encoding?.let { append("encoding", it) }
            }
        }.build().toString()
    }

    private fun formatNumeric(d: Double): String =
        if (d == d.toLong().toDouble()) {
            d.toLong().toString()
        } else {
            d.toString()
        }
}
