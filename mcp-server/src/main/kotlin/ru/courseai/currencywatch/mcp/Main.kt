package ru.courseai.currencywatch.mcp

import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.ktor.utils.io.streams.asInput
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.courseai.currencywatch.shared.CbrApiService
import ru.courseai.currencywatch.shared.ExchangeRateRepository
import ru.courseai.currencywatch.shared.Scheduler
import ru.courseai.currencywatch.shared.createCbrHttpClient
import ru.courseai.currencywatch.shared.createJvmDatabaseHelper
import ru.courseai.currencywatch.shared.model.CurrencySummary
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ru.courseai.currencywatch.mcp")

fun main(args: Array<String>) = runBlocking {
    if (args.contains("-h") || args.contains("--help")) {
        printUsage()
        exitProcess(0)
    }

    val syncInterval = parseSyncIntervalOrExit(args)

    val dataDir = File(System.getProperty("user.home"), ".currency-watch")
    dataDir.mkdirs()
    logger.info("Каталог данных и логов: {}", dataDir.absolutePath)
    logger.info("Интервал синхронизации с ЦБ: {} (задаётся аргументом --sync-interval, по умолчанию PT1H)", syncInterval)

    val dbFile = File(dataDir, "currency.db")
    val databaseHelper = createJvmDatabaseHelper(dbFile)
    val httpClient = createCbrHttpClient().apply {
        // closed when process exits; keep alive for MCP lifetime
    }
    val cbrApi = CbrApiService(httpClient)
    val repository = ExchangeRateRepository(databaseHelper, cbrApi)

    val appScope = CoroutineScope(coroutineContext + SupervisorJob())
    val scheduler = Scheduler(appScope)
    scheduler.startPeriodic(syncInterval) {
        logger.info("Планировщик: синхронизация с ЦБ (по расписанию)")
        repository.syncAndStore()
        logger.info("Планировщик: синхронизация завершена")
    }
    launch {
        logger.info("Первая синхронизация с ЦБ при старте")
        try {
            repository.syncAndStore()
            logger.info("Первая синхронизация завершена")
        } catch (e: Exception) {
            logger.error("Ошибка первой синхронизации", e)
        }
    }

    runMcpStdio(repository)
}

private suspend fun runMcpStdio(repository: ExchangeRateRepository) {
    logger.info("Запуск MCP (stdio). Логи пишутся в файл {} и в stderr.", File(System.getProperty("user.home"), ".currency-watch/mcp-server.log").absolutePath)

    val server = Server(
        serverInfo = Implementation(
            name = "currency-watch",
            version = "0.1.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    )

    server.addTool(
        name = "get_currency_summary",
        description = "Агрегированный отчёт по курсам валют ЦБ РФ из локальной БД за последние N часов.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("hours") {
                    put("type", "integer")
                    put("description", "Период в часах (по умолчанию 24)")
                }
                putJsonObject("currencies") {
                    put("type", "array")
                    put(
                        "description",
                        "Список буквенных кодов валют (например USD, EUR). Если не задан — все валюты в окне.",
                    )
                    put(
                        "items",
                        buildJsonObject {
                            put("type", "string")
                        },
                    )
                }
            },
            required = emptyList(),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val args = request.arguments as? JsonObject
        val hours = args?.get("hours")?.jsonPrimitive?.intOrNull ?: 24
        val currencies = args?.get("currencies")?.jsonArray?.toStringSet()
        logger.info("Вызов get_currency_summary: hours={}, currencies={}", hours, currencies)
        val summaries = repository.getSummary(hours, currencies)
        val json = Json.encodeToString(summaries)
        val text = formatSummaryText(summaries, hours)
        CallToolResult(
            content = listOf(
                TextContent(text = "$text\n\nJSON:\n$json"),
            ),
        )
    }

    val transport = StdioServerTransport(
        inputStream = System.`in`.asInput(),
        outputStream = System.out.asSink().buffered(),
    )

    val session = server.createSession(transport)
    val done = Job()
    session.onClose {
        done.complete()
    }
    done.join()
}

private fun JsonArray.toStringSet(): Set<String>? {
    val set = mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
    return set.ifEmpty { null }
}

private fun formatSummaryText(summaries: List<CurrencySummary>, hours: Int): String {
    if (summaries.isEmpty()) {
        return "Нет данных за последние $hours ч. Синхронизируйте с ЦБ (планировщик или перезапуск сервера)."
    }
    val lines = summaries.joinToString("\n") {
        "${it.charCode}: avg=${"%.4f".format(it.avg)} min=${"%.4f".format(it.min)} max=${"%.4f".format(it.max)} Δ=${"%.4f".format(it.change)} (n=${it.sampleCount})"
    }
    return "Период: $hours ч.\n$lines"
}

private fun printUsage() {
    System.err.println(
        """
        Валютный дозор MCP — использование:
          --sync-interval=<длительность>   интервал фоновой синхронизации с ЦБ (ISO-8601), по умолчанию PT1H
                                           примеры: PT30M, PT1H, PT2H, PT15M
          -h, --help                       эта справка

        Примеры:
          gradlew.bat :mcp-server:run --args="--sync-interval=PT30M"
        """.trimIndent(),
    )
}

private fun parseSyncIntervalOrExit(args: Array<String>): Duration {
    val raw = when {
        args.any { it.startsWith("--sync-interval=") } ->
            args.first { it.startsWith("--sync-interval=") }.substringAfter("=", "")
        else -> {
            val idx = args.indexOf("--sync-interval")
            when {
                idx < 0 -> null
                idx + 1 >= args.size -> {
                    System.err.println("Ошибка: после --sync-interval укажите длительность, например: --sync-interval PT30M")
                    printUsage()
                    exitProcess(1)
                }
                else -> args[idx + 1]
            }
        }
    }
    if (raw == null) {
        return 1.hours
    }
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        System.err.println("Ошибка: после --sync-interval нужна длительность, например PT30M")
        printUsage()
        exitProcess(1)
    }
    val parsed = try {
        Duration.parse(trimmed)
    } catch (_: Exception) {
        System.err.println("Ошибка: не удалось разобрать --sync-interval=\"$trimmed\" (ожидается ISO-8601, например PT1H или PT30M)")
        printUsage()
        exitProcess(1)
    }
    if (parsed <= Duration.ZERO) {
        System.err.println("Ошибка: --sync-interval должен быть положительным")
        exitProcess(1)
    }
    if (parsed < 1.minutes) {
        System.err.println("Ошибка: минимальный интервал — 1 минута (PT1M)")
        exitProcess(1)
    }
    return parsed
}
