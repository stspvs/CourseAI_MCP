package ru.courseai.currencywatch.mcp

import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.utils.io.streams.asInput
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
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
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ru.courseai.currencywatch.mcp")

/** Версия сервера — при старте пишется в лог; если в логе нет этой строки, запущена старая сборка. */
private const val MCP_SERVER_VERSION = "0.1.0"

private enum class TransportMode {
    STDIO,
    HTTP,
}

private data class LauncherConfig(
    val transport: TransportMode,
    val syncInterval: Duration,
    val httpHost: String,
    val httpPort: Int,
    val httpPath: String,
)

fun main(args: Array<String>) {
    if (args.contains("-h") || args.contains("--help")) {
        printUsage()
        exitProcess(0)
    }

    val config = parseLauncherConfigOrExit(args)

    val dataDir = File(System.getProperty("user.home"), ".currency-watch")
    dataDir.mkdirs()
    logger.info(
        "Currency-watch MCP v{} | каталог данных: {}",
        MCP_SERVER_VERSION,
        dataDir.absolutePath,
    )
    logger.info("Интервал синхронизации с ЦБ: {}", config.syncInterval)
    logger.info("Транспорт MCP: {}", config.transport)

    val dbFile = File(dataDir, "currency.db")
    logger.info("Файл SQLite: {}", dbFile.absolutePath)
    val databaseHelper = createJvmDatabaseHelper(dbFile)
    val httpClient = createCbrHttpClient()
    val cbrApi = CbrApiService(httpClient)
    val repository = ExchangeRateRepository(databaseHelper, cbrApi)

    runBlocking {
        logger.info("Первая синхронизация с ЦБ при старте")
        try {
            val saved = repository.syncAndStore()
            logger.info(
                "Первая синхронизация завершена: в БД сохранено {} строк курсов (0 — нет ответа от ЦБ, пустой XML или ошибка сети/парсинга)",
                saved,
            )
        } catch (e: Exception) {
            logger.error("Ошибка первой синхронизации", e)
        }
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val scheduler = Scheduler(appScope)
    scheduler.startPeriodic(config.syncInterval) {
        logger.info("Планировщик: синхронизация с ЦБ (по расписанию)")
        try {
            val saved = repository.syncAndStore()
            logger.info("Планировщик: синхронизация завершена, сохранено {} строк", saved)
        } catch (e: Exception) {
            logger.error("Планировщик: ошибка синхронизации с ЦБ", e)
        }
    }

    when (config.transport) {
        TransportMode.STDIO -> runBlocking { runMcpStdio(repository, dataDir) }
        TransportMode.HTTP -> runMcpHttp(repository, config, dataDir)
    }
}

private fun buildMcpServer(repository: ExchangeRateRepository, dataDir: File): Server {
    val server = Server(
        serverInfo = Implementation(
            name = "currency-watch",
            version = MCP_SERVER_VERSION,
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
        instructions = """
            Назначение: курсы валют по данным Центрального банка Российской Федерации (официальные курсы ЦБ РФ).
            Сервер хранит историю в локальной SQLite и периодически синхронизируется с API ЦБ. Инструменты get_latest_currency_rates и get_currency_summary читают уже загруженные в локальную БД данные (без сетевого запроса при каждом вызове). Инструмент get_currency_rates_on_date при вызове запрашивает дневной курс у API ЦБ на указанную календарную дату.

            Когда вызывать: актуальный/последний курс по данным ЦБ (на момент последней синхронизации) — get_latest_currency_rates; динамику, среднее или сравнение за период — get_currency_summary; курс на конкретную прошлую или текущую дату по календарю — get_currency_rates_on_date.

            Инструмент get_latest_currency_rates: последний сохранённый курс только по явно перечисленным валютам (параметр currencies обязателен, например ["USD"] или ["USD","EUR"]).

            Инструмент get_currency_summary: агрегаты за последние N часов. Параметр hours — окно; currencies — опционально буквенные коды как в XML ЦБ (USD, EUR, CNY); без currencies — все валюты в окне. Не передавайте русские названия валют в currencies.

            Инструмент get_currency_rates_on_date: дневной курс на дату date (YYYY-MM-DD, DD.MM.YYYY или 20240601) для указанных currencies; обращается к API ЦБ при вызове.

            Инструмент save_text: сохраняет текст в файл внутри каталога данных приложения (~/.currency-watch). Параметры: path — относительный путь к файлу (например notes/memo.txt); content — текст в UTF-8; append — опционально true для дописывания в конец существующего файла, иначе файл создаётся или перезаписывается.
        """.trimIndent(),
    ) {
        addTool(
            name = "get_latest_currency_rates",
            description = """
                Последние (актуальные) курсы валют ЦБ РФ по данным из локальной БД только для указанных кодов: для каждой запрошенной валюты — последняя запись после синхронизации с ЦБ.
                Используйте, когда нужен текущий курс без статистики за период. Параметр currencies обязателен (хотя бы одна валюта).
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("currencies") {
                        put("type", "array")
                        put(
                            "description",
                            "Обязательный список кодов ISO 4217 (USD, EUR, CNY, …), минимум один код. Возвращаются только эти валюты.",
                        )
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "string")
                            },
                        )
                        put("minItems", 1)
                    }
                },
                required = listOf("currencies"),
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
        ) { request ->
            val args = request.arguments as? JsonObject
            val currencies = args?.get("currencies")?.jsonArray?.toCurrencyCodeStringSet()
            if (currencies.isNullOrEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Укажите непустой массив currencies с кодами валют (например [\"USD\"] или [\"USD\",\"EUR\"]).",
                        ),
                    ),
                )
            }
            logger.info("Инструмент get_latest_currency_rates: currencies={}", currencies)
            val rates = repository.getLatestRates(currencies)
            logger.info(
                "get_latest_currency_rates: ответ — {} записей (0 = в БД нет курсов по запрошенным кодам)",
                rates.size,
            )
            val text = McpSummaryMessages.formatLatestRatesText(rates)
            CallToolResult(
                content = listOf(
                    TextContent(text = text),
                ),
            )
        }

        addTool(
            name = "get_currency_summary",
            description = """
                Курсы валют ЦБ РФ: агрегированный отчёт (среднее, min, max, изменение за период) по данным из локальной БД за последние N часов.
                Используйте для вопросов о курсах валют, динамике и сравнении за выбранный период.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("hours") {
                        put("type", "integer")
                        put(
                            "description",
                            "Сколько последних часов учитывать (окно для курсов). По умолчанию 24.",
                        )
                    }
                    putJsonObject("currencies") {
                        put("type", "array")
                        put(
                            "description",
                            "Только буквенные коды как в XML ЦБ (USD, EUR, CNY, …), не названия на русском. Если не указать — сводка по всем валютам в окне.",
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
            val currencies = args?.get("currencies")?.jsonArray?.toCurrencyCodeStringSet()
            logger.info("Инструмент get_currency_summary: hours={}, currencies={}", hours, currencies)
            val summaries = repository.getSummary(hours, currencies)
            val snapshotsInWindow = repository.snapshotCountInWindow(hours)
            logger.info(
                "get_currency_summary: сводок={}, снимков в окне {} ч.={}",
                summaries.size,
                hours,
                snapshotsInWindow,
            )
            val currencyFilterApplied = !currencies.isNullOrEmpty()
            val text = if (summaries.isEmpty()) {
                formatSummaryEmptyText(
                    repository = repository,
                    hours = hours,
                    currencyFilterApplied = currencyFilterApplied,
                    snapshotsInWindow = snapshotsInWindow,
                    requestedCurrencies = currencies,
                )
            } else {
                McpSummaryMessages.formatSummaryText(summaries, hours)
            }
            CallToolResult(
                content = listOf(
                    TextContent(text = text),
                ),
            )
        }

        addTool(
            name = "get_currency_rates_on_date",
            description = """
                Дневные курсы валют ЦБ РФ на указанную календарную дату для заданных кодов валют.
                При каждом вызове выполняется запрос к API ЦБ (не чтение из локальной БД). Используйте для исторической даты или когда нужен курс именно на день из календаря.
                Параметры: currencies (обязателен), date — YYYY-MM-DD, DD.MM.YYYY или число 20240601 в JSON.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("currencies") {
                        put("type", "array")
                        put(
                            "description",
                            "Обязательный список кодов ISO 4217 (USD, EUR, CNY, …), минимум один код.",
                        )
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "string")
                            },
                        )
                        put("minItems", 1)
                    }
                    putJsonObject("date") {
                        put("type", "string")
                        put(
                            "description",
                            "Дата: YYYY-MM-DD (2024-06-01), DD.MM.YYYY (01.06.2024) или строка из 8 цифр 20240601; в JSON допустимо и число 20240601.",
                        )
                    }
                },
                required = listOf("currencies", "date"),
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
        ) { request ->
            val args = request.arguments as? JsonObject
            when (val parsed = parseCurrencyRatesOnDateArgs(args)) {
                is ParseCurrencyRatesOnDateResult.Failure ->
                    CallToolResult(
                        content = listOf(TextContent(text = parsed.message)),
                    )
                is ParseCurrencyRatesOnDateResult.Success -> {
                    val currencies = parsed.currencies
                    val date = parsed.date
                    logger.info("Инструмент get_currency_rates_on_date: currencies={}, date={}", currencies, date)
                    val text = try {
                        val rates = repository.getRatesForDate(currencies, date)
                        logger.info("get_currency_rates_on_date: записей после фильтра={}", rates.size)
                        McpSummaryMessages.formatRatesOnDateText(date, rates)
                    } catch (e: Exception) {
                        logger.error("get_currency_rates_on_date: ошибка запроса к ЦБ", e)
                        "Не удалось получить курсы на дату $date: ${e.message ?: e::class.simpleName}"
                    }
                    CallToolResult(
                        content = listOf(
                            TextContent(text = text),
                        ),
                    )
                }
            }
        }

        addTool(
            name = "save_text",
            description = """
                Сохраняет переданный текст в файл внутри каталога данных (~/.currency-watch) по относительному пути path (UTF-8).
                Используйте для записи заметок, экспорта или отчётов на диск пользователя в разрешённой папке. Параметр append=true дописывает в конец существующего файла.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", "string")
                        put(
                            "description",
                            "Относительный путь к файлу внутри каталога данных (например notes/foo.txt). Абсолютные пути и выход за пределы каталога запрещены.",
                        )
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Текст для записи в кодировке UTF-8.")
                    }
                    putJsonObject("append") {
                        put("type", "boolean")
                        put(
                            "description",
                            "Если true — дописать в конец существующего файла; если false или не указано — создать новый файл или перезаписать существующий.",
                        )
                    }
                },
                required = listOf("path", "content"),
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = false, openWorldHint = true),
        ) { request ->
            val args = request.arguments as? JsonObject
            val pathRaw = args?.get("path")?.jsonPrimitive?.contentOrNull
            val content = args?.get("content")?.jsonPrimitive?.contentOrNull
            val append = (args?.get("append") as? JsonPrimitive)?.booleanOrNull ?: false
            if (pathRaw.isNullOrBlank()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(text = "Укажите непустой строковый параметр path (относительный путь к файлу)."),
                    ),
                )
            }
            if (content == null) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(text = "Укажите параметр content (текст для сохранения)."),
                    ),
                )
            }
            logger.info(
                "Инструмент save_text: path={}, append={}, contentLength={}",
                pathRaw,
                append,
                content.length,
            )
            when (val resolved = resolveSandboxFile(dataDir, pathRaw)) {
                is SandboxPathResult.Err ->
                    CallToolResult(content = listOf(TextContent(text = resolved.message)))
                is SandboxPathResult.Ok -> {
                    val outcome = writeSandboxUtf8(resolved.file, content, append)
                    val text = outcome.fold(
                        onSuccess = { o ->
                            "Сохранено: ${o.absolutePath} (режим: ${o.modeLabel}, записано байт: ${o.bytesWritten})."
                        },
                        onFailure = { e ->
                            logger.error("save_text: ошибка записи файла", e)
                            "Не удалось записать файл: ${e.message ?: e::class.simpleName}"
                        },
                    )
                    CallToolResult(content = listOf(TextContent(text = text)))
                }
            }
        }
    }

    return server
}

private suspend fun runMcpStdio(repository: ExchangeRateRepository, dataDir: File) {
    logger.info(
        "Запуск MCP (stdio). Логи пишутся в файл {} и в stderr.",
        File(System.getProperty("user.home"), ".currency-watch/mcp-server.log").absolutePath,
    )

    val server = buildMcpServer(repository, dataDir)

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

private fun runMcpHttp(repository: ExchangeRateRepository, config: LauncherConfig, dataDir: File) {
    val url = "http://${config.httpHost}:${config.httpPort}${normalizeHttpPath(config.httpPath)}"
    logger.info("Запуск MCP (Streamable HTTP). Подключайте клиент по адресу: {}", url)
    logger.info("Логи: {} и stderr.", File(System.getProperty("user.home"), ".currency-watch/mcp-server.log").absolutePath)

    embeddedServer(Netty, host = config.httpHost, port = config.httpPort) {
        configureStreamableMcp(repository, dataDir, normalizeHttpPath(config.httpPath))
    }.start(wait = true)
}

private fun normalizeHttpPath(path: String): String {
    val p = path.trim().ifEmpty { "/mcp" }
    return if (p.startsWith("/")) p else "/$p"
}

private fun Application.configureStreamableMcp(repository: ExchangeRateRepository, dataDir: File, path: String) {
    // ContentNegotiation + McpJson ставит сам mcpStreamableHttp() — дублировать нельзя (порядок плагинов / SSE).
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowNonSimpleContentTypes = true
        allowHeader("Mcp-Session-Id")
        allowHeader("Mcp-Protocol-Version")
        exposeHeader("Mcp-Session-Id")
        exposeHeader("Mcp-Protocol-Version")
    }
    mcpStreamableHttp(path = path) {
        buildMcpServer(repository, dataDir)
    }
}

private suspend fun formatSummaryEmptyText(
    repository: ExchangeRateRepository,
    hours: Int,
    currencyFilterApplied: Boolean,
    snapshotsInWindow: Int,
    requestedCurrencies: Set<String>?,
): String {
    val totalInWindow = snapshotsInWindow
    val maxFetchedAt = repository.getMaxFetchedAtMillis()
    val now = System.currentTimeMillis()
    logger.info(
        "get_currency_summary: пустой ответ — снимковВОкне={}, maxFetchedAtMs={}, фильтрПоВалютам={}",
        totalInWindow,
        maxFetchedAt,
        currencyFilterApplied,
    )
    if (totalInWindow > 0 && currencyFilterApplied) {
        val available = repository.distinctCurrencyCodesInWindow(hours)
        return McpSummaryMessages.emptySummaryCurrencyMismatch(
            hours = hours,
            totalInWindow = totalInWindow,
            requestedCurrencies = requestedCurrencies,
            availableCodes = available,
        )
    }
    if (totalInWindow == 0 && maxFetchedAt != null) {
        val ageHours = (now - maxFetchedAt).coerceAtLeast(0L) / 3_600_000L
        return McpSummaryMessages.emptySummaryStaleWindow(hours, ageHours)
    }
    return McpSummaryMessages.emptySummaryNoRowsInDb(hours)
}

private fun printUsage() {
    System.err.println(
        """
        Валютный дозор MCP — использование:
          --transport=stdio|http          способ доступа: stdio (Claude Desktop) или HTTP на localhost (по умолчанию stdio)
          --sync-interval=<длительность>  интервал фоновой синхронизации с ЦБ (ISO-8601), по умолчанию PT1H
          --http-host=<хост>              только для --transport=http, по умолчанию 127.0.0.1
          --http-port=<порт>              только для --transport=http, по умолчанию 3000
          --http-path=<путь>              только для --transport=http, по умолчанию /mcp
          -h, --help                      эта справка

        Примеры:
          gradlew.bat :mcp-server:run --args="--transport=http --http-port=3000"
          gradlew.bat :mcp-server:run --args="--sync-interval=PT30M"
        """.trimIndent(),
    )
}

private fun parseLauncherConfigOrExit(args: Array<String>): LauncherConfig {
    val transport = parseTransport(args)
    val syncInterval = parseSyncIntervalOrExit(args)
    val httpHost = parseStringArg(args, "--http-host=", "127.0.0.1") { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    } ?: "127.0.0.1"
    val httpPort = parseIntArg(args, "--http-port=", 3000) { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1]?.toIntOrNull() else null
    } ?: 3000
    val httpPath = parseStringArg(args, "--http-path=", "/mcp") { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    } ?: "/mcp"

    if (transport == TransportMode.HTTP && (httpPort !in 1..65535)) {
        System.err.println("Ошибка: --http-port должен быть в диапазоне 1–65535")
        exitProcess(1)
    }

    return LauncherConfig(
        transport = transport,
        syncInterval = syncInterval,
        httpHost = httpHost,
        httpPort = httpPort,
        httpPath = httpPath,
    )
}

private fun parseTransport(args: Array<String>): TransportMode {
    val fromEquals = args.firstOrNull { it.startsWith("--transport=") }?.substringAfter("=", "")?.trim()?.lowercase()
    if (fromEquals != null) {
        return when (fromEquals) {
            "stdio" -> TransportMode.STDIO
            "http" -> TransportMode.HTTP
            "" -> {
                System.err.println("Ошибка: укажите значение --transport=stdio или --transport=http")
                printUsage()
                exitProcess(1)
            }
            else -> {
                System.err.println("Ошибка: неизвестный --transport=$fromEquals (ожидается stdio или http)")
                exitProcess(1)
            }
        }
    }
    val idx = args.indexOf("--transport")
    if (idx >= 0) {
        if (idx + 1 >= args.size) {
            System.err.println("Ошибка: после --transport укажите stdio или http")
            printUsage()
            exitProcess(1)
        }
        return when (args[idx + 1].lowercase()) {
            "stdio" -> TransportMode.STDIO
            "http" -> TransportMode.HTTP
            else -> {
                System.err.println("Ошибка: ожидался stdio или http")
                exitProcess(1)
            }
        }
    }
    return TransportMode.STDIO
}

private fun parseStringArg(
    args: Array<String>,
    prefix: String,
    fallback: String,
    fromPair: (Int) -> String?,
): String {
    val eq = args.firstOrNull { it.startsWith(prefix) }?.substringAfter("=", "")
    if (eq != null && eq.isNotBlank()) return eq.trim()
    val idx = args.indexOf(prefix.trimEnd('='))
    if (idx >= 0) {
        val v = fromPair(idx)
        if (v != null && v.isNotBlank()) return v.trim()
    }
    return fallback
}

private fun parseIntArg(
    args: Array<String>,
    prefix: String,
    fallback: Int,
    fromPair: (Int) -> Int?,
): Int {
    val eq = args.firstOrNull { it.startsWith(prefix) }?.substringAfter("=", "")?.toIntOrNull()
    if (eq != null) return eq
    val key = prefix.trimEnd('=')
    val idx = args.indexOf(key)
    if (idx >= 0) {
        val v = fromPair(idx)
        if (v != null) return v
    }
    return fallback
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
    return try {
        parseAndValidateSyncInterval(trimmed)
    } catch (e: IllegalArgumentException) {
        System.err.println("Ошибка: ${e.message}")
        printUsage()
        exitProcess(1)
    }
}
