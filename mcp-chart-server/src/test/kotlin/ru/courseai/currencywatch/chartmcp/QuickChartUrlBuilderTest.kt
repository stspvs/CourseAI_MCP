package ru.courseai.currencywatch.chartmcp

import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickChartUrlBuilderTest {

    @Test
    fun build_minimalChart_encodesChartQueryAndOptionalParams() {
        val chartJson = """{"type":"bar","data":{"labels":["a"],"datasets":[{"label":"x","data":[1]}]}}"""
        val url = QuickChartUrlBuilder.build(
            QuickChartUrlBuilder.Params(
                chartJson = chartJson,
                width = 400,
                height = 250,
                devicePixelRatio = 2.0,
                backgroundColor = "#fff",
                version = "4",
                format = "png",
                encoding = "url",
            ),
        )

        assertTrue(url.startsWith("https://quickchart.io/chart?"), "unexpected base: $url")
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("quickchart.io", uri.host)
        assertEquals("/chart", uri.path)

        val decodedChart = parseQuery(uri.query)["chart"]
        assertEquals(chartJson, decodedChart)
        assertEquals("400", parseQuery(uri.query)["width"])
        assertEquals("250", parseQuery(uri.query)["height"])
        assertEquals("2", parseQuery(uri.query)["devicePixelRatio"])
        assertEquals("#fff", parseQuery(uri.query)["backgroundColor"])
        assertEquals("4", parseQuery(uri.query)["version"])
        assertEquals("png", parseQuery(uri.query)["format"])
        assertEquals("url", parseQuery(uri.query)["encoding"])
    }

    @Test
    fun build_onlyChart_noOptionalParams() {
        val chartJson = """{"type":"line","data":{"labels":[],"datasets":[]}}"""
        val url = QuickChartUrlBuilder.build(QuickChartUrlBuilder.Params(chartJson = chartJson))
        val uri = URI(url)
        val q = parseQuery(uri.query)
        assertEquals(1, q.size)
        assertEquals(chartJson, q["chart"])
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val k = pair.substring(0, idx)
            val vEncoded = pair.substring(idx + 1)
            val v = java.net.URLDecoder.decode(vEncoded, StandardCharsets.UTF_8)
            k to v
        }.toMap()
    }
}
