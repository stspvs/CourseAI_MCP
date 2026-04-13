package ru.courseai.currencywatch.shared

actual fun decodeCbrXmlBytes(bytes: ByteArray): String =
    bytes.decodeToString()
