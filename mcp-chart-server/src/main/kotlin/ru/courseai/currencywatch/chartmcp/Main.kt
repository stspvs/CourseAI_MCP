package ru.courseai.currencywatch.chartmcp

import kotlin.system.exitProcess
import kotlinx.coroutines.Job
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ru.courseai.currencywatch.chartmcp")

private const val CHART_MCP_VERSION = "0.1.0"

private enum class TransportMode {
    STDIO,
    HTTP,
}

private data class LauncherConfig(
    val transport: TransportMode,
    val httpHost: String,
    val httpPort: Int,
    val httpPath: String,
)

private val compactJson = Json {
    prettyPrint = false
    encodeDefaults = false
    ignoreUnknownKeys = true
}

fun main(args: Array<String>) {
    if (args.contains("-h") || args.contains("--help")) {
        printUsage()
        exitProcess(0)
    }

    val config = parseLauncherConfigOrExit(args)

    logger.info(
        "QuickChart MCP v{} | транспорт: {} (HTTP: {}:{}{})",
        CHART_MCP_VERSION,
        config.transport,
        config.httpHost,
        config.httpPort,
        normalizeHttpPath(config.httpPath),
    )

    when (config.transport) {
        TransportMode.STDIO -> runBlocking { runMcpStdio() }
        TransportMode.HTTP -> runMcpHttp(config)
    }
}

private fun buildChartMcpServer(): Server {
    val server = Server(
        serverInfo = Implementation(
            name = "quickchart-mcp",
            version = CHART_MCP_VERSION,
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
        instructions = """
            Сервер только строит ссылку на картинку графика (Chart.js → PNG/WebP/SVG/PDF через QuickChart). Сам он не загружает курсы валют и не ходит в интернет за данными: числа и подписи нужно передать в объекте chart уже готовыми.

            Когда вызывать build_quickchart_url:
            • пользователь просит «график», «диаграмму», «визуализировать ряд», «столбики/линия/круговая», сравнить несколько рядов по меткам оси X;
            • нужна одна HTTPS-ссылка на готовое изображение диаграммы (ответ инструмента — только URL, без Markdown-обёртки).
            Не вызывать только ради текста без графика; не использовать для получения курсов ЦБ — для этого другой MCP.

            Параметры по смыслу: chart (обязательно) — JSON Chart.js; width/height — размер картинки; остальные — см. описания полей инструмента.

            Ограничение: слишком большой `chart` раздувает GET-URL (лимиты прокси/клиента). Сократите точки, уберите лишние поля options или упростите подписи.
        """.trimIndent(),
    ) {
        addTool(
            name = "build_quickchart_url",
            description = """
                Собирает URL изображения графика на [QuickChart](https://quickchart.io/chart): по переданному Chart.js-конфигу возвращает в ответе только одну строку — HTTPS URL к PNG/WebP/SVG/PDF (без префиксов, без Markdown, без дублирования).

                Вызывайте, когда нужно показать график по уже известным числам: временной ряд, сравнение категорий, несколько линий/столбцов, круговая/линейная/столбчатая диаграмма. Не вызывайте, если достаточно таблицы или текста; этот инструмент не запрашивает курсы валют и не подставляет данные из внешних API — всё задаётся в `chart`.

                Обязательно: chart — объект Chart.js минимум с `type` (например `line`, `bar`, `pie`) и `data`: `labels` (подписи по X/категориям) и `datasets` (массив рядов с `label`, `data` — числа). Опционально `options` (легенда, оси, заголовок) по документации Chart.js.

                Опционально для картинки: width, height (пиксели), devicePixelRatio, backgroundColor, version (Chart.js на стороне QuickChart), format (png|webp|svg|pdf), encoding (url|base64). Если не переданы — действуют значения по умолчанию QuickChart.
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("chart") {
                        put("type", "object")
                        put(
                            "description",
                            "Обязательный Chart.js-конфиг: type (line|bar|pie|…), data.labels — подписи точек/категорий, data.datasets — ряды: у каждого label (легенда) и data (массив чисел). Пример bar: {\"type\":\"bar\",\"data\":{\"labels\":[\"Q1\",\"Q2\"],\"datasets\":[{\"label\":\"Продажи\",\"data\":[10,20]}]}}. Опционально options для легенды/осей.",
                        )
                    }
                    putJsonObject("width") {
                        put("type", "integer")
                        put(
                            "description",
                            "Ширина итогового изображения в пикселях. Не указывать — по умолчанию 500 у QuickChart; задайте явно для узких/широких макетов.",
                        )
                    }
                    putJsonObject("height") {
                        put("type", "integer")
                        put(
                            "description",
                            "Высота изображения в пикселях. Не указывать — по умолчанию 300 у QuickChart.",
                        )
                    }
                    putJsonObject("devicePixelRatio") {
                        put("type", "number")
                        put(
                            "description",
                            "Чёткость: 1 — обычная плотность, 2 — выше (часто для «retina»). По умолчанию на стороне QuickChart — 2.",
                        )
                    }
                    putJsonObject("backgroundColor") {
                        put("type", "string")
                        put(
                            "description",
                            "Цвет фона за графиком: #rrggbb, rgb(), hsl() или имя (white). По умолчанию прозрачный.",
                        )
                    }
                    putJsonObject("version") {
                        put("type", "string")
                        put(
                            "description",
                            "Версия движка Chart.js на QuickChart: обычно \"2\" или \"4\", если нужны возможности конкретной ветки.",
                        )
                    }
                    putJsonObject("format") {
                        put("type", "string")
                        put(
                            "description",
                            "Файл картинки: png (типично для чата), webp, svg (вектор) или pdf.",
                        )
                    }
                    putJsonObject("encoding") {
                        put("type", "string")
                        put(
                            "description",
                            "Что отдаёт QuickChart при запросе этого URL: url или base64 (параметр API QuickChart).",
                        )
                    }
                },
                required = listOf("chart"),
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
        ) { request ->
            val args = request.arguments as? JsonObject
            val chart = args?.get("chart") as? JsonObject
            if (chart == null) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Укажите обязательный объект chart (JSON-конфигурация Chart.js).",
                        ),
                    ),
                )
            }
            val chartJson = compactJson.encodeToString(JsonObject.serializer(), chart)
            val width = args.parseOptionalInt("width")
            val height = args.parseOptionalInt("height")
            val devicePixelRatio = args.parseOptionalDouble("devicePixelRatio")
            val backgroundColor = args.parseOptionalString("backgroundColor")
            val version = args.parseOptionalString("version")
            val format = args.parseOptionalString("format")
            val encoding = args.parseOptionalString("encoding")
            logger.info(
                "build_quickchart_url: chartJsonLength={}, width={}, height={}",
                chartJson.length,
                width,
                height,
            )
            val text = try {
                val url = QuickChartUrlBuilder.build(
                    QuickChartUrlBuilder.Params(
                        chartJson = chartJson,
                        width = width,
                        height = height,
                        devicePixelRatio = devicePixelRatio,
                        backgroundColor = backgroundColor,
                        version = version,
                        format = format,
                        encoding = encoding,
                    ),
                )
                url
            } catch (e: Exception) {
                logger.error("build_quickchart_url: ошибка сборки URL", e)
                "Не удалось собрать URL QuickChart: ${e.message ?: e::class.simpleName}"
            }
            CallToolResult(content = listOf(TextContent(text = text)))
        }
    }
    return server
}

private fun JsonObject?.parseOptionalInt(key: String): Int? {
    if (this == null) return null
    val p = this[key]?.jsonPrimitive ?: return null
    p.intOrNull?.let { return it }
    return p.contentOrNull?.trim()?.toIntOrNull()
}

private fun JsonObject?.parseOptionalDouble(key: String): Double? {
    if (this == null) return null
    val p = this[key]?.jsonPrimitive ?: return null
    return p.contentOrNull?.trim()?.toDoubleOrNull()
}

private fun JsonObject?.parseOptionalString(key: String): String? {
    if (this == null) return null
    val raw = (this[key] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
    return raw.ifEmpty { null }
}

private suspend fun runMcpStdio() {
    val logPath = java.io.File(System.getProperty("user.home"), ".currency-watch/mcp-chart-server.log").absolutePath
    logger.info("Запуск MCP (stdio). Логи: {} и stderr.", logPath)

    val server = buildChartMcpServer()

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

private fun runMcpHttp(config: LauncherConfig) {
    val path = normalizeHttpPath(config.httpPath)
    val url = "http://${config.httpHost}:${config.httpPort}$path"
    logger.info("Запуск MCP (Streamable HTTP). Подключайте клиент по адресу: {}", url)
    logger.info("Логи: {} и stderr.", java.io.File(System.getProperty("user.home"), ".currency-watch/mcp-chart-server.log").absolutePath)

    embeddedServer(Netty, host = config.httpHost, port = config.httpPort) {
        configureStreamableMcp(path)
    }.start(wait = true)
}

private fun normalizeHttpPath(path: String): String {
    val p = path.trim().ifEmpty { "/mcp" }
    return if (p.startsWith("/")) p else "/$p"
}

private fun Application.configureStreamableMcp(path: String) {
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
        buildChartMcpServer()
    }
}

private fun printUsage() {
    System.err.println(
        """
        QuickChart MCP — использование:
          --transport=stdio|http          способ доступа: stdio (Claude Desktop) или HTTP на localhost (по умолчанию stdio)
          --http-host=<хост>              только для --transport=http, по умолчанию 127.0.0.1
          --http-port=<порт>             только для --transport=http, по умолчанию 3001 (валютный MCP часто на 3000)
          --http-path=<путь>             только для --transport=http, по умолчанию /mcp
          -h, --help                      эта справка

        Примеры:
          gradlew.bat :mcp-chart-server:run --args="--transport=http --http-port=3001"
          gradlew.bat :mcp-chart-server:run
        """.trimIndent(),
    )
}

private fun parseLauncherConfigOrExit(args: Array<String>): LauncherConfig {
    val transport = parseTransport(args)
    val httpHost = parseStringArg(args, "--http-host=", "127.0.0.1") { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    } ?: "127.0.0.1"
    val httpPort = parseIntArg(args, "--http-port=", 3001) { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1]?.toIntOrNull() else null
    } ?: 3001
    val httpPath = parseStringArg(args, "--http-path=", "/mcp") { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    } ?: "/mcp"

    if (transport == TransportMode.HTTP && (httpPort !in 1..65535)) {
        System.err.println("Ошибка: --http-port должен быть в диапазоне 1–65535")
        exitProcess(1)
    }

    return LauncherConfig(
        transport = transport,
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
): String? {
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
): Int? {
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
