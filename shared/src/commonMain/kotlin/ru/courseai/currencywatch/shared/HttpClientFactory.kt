package ru.courseai.currencywatch.shared

import io.ktor.client.HttpClient

expect fun createCbrHttpClient(): HttpClient
