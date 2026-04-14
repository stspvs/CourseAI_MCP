package ru.courseai.currencywatch.shared.model

/** Одна точка из ответа XML_dynamic.asp ЦБ РФ. */
data class CbrDynamicRecord(
    val recordDate: String,
    val valueRub: Double,
)

/** Метаданные и ряд значений из `<ValCurs>` (динамика курса). */
data class CbrDynamicSeries(
    val valuteId: String,
    val dateRange1: String?,
    val dateRange2: String?,
    val name: String?,
    val records: List<CbrDynamicRecord>,
)
