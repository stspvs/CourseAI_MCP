package ru.courseai.currencywatch.shared

import java.nio.charset.Charset

actual fun decodeCbrXmlBytes(bytes: ByteArray): String =
    String(bytes, Charset.forName("windows-1251"))
