package ru.courseai.currencywatch.mcp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Разбор и проверка `--sync-interval` (ISO-8601 duration). Без [exitProcess] — для тестов.
 * Сообщения — для вывода пользователю в stderr.
 */
internal fun parseAndValidateSyncInterval(trimmed: String): Duration {
    if (trimmed.isEmpty()) {
        throw IllegalArgumentException("после --sync-interval нужна длительность, например PT30M")
    }
    val parsed = try {
        Duration.parse(trimmed)
    } catch (_: Exception) {
        throw IllegalArgumentException(
            "не удалось разобрать --sync-interval=\"$trimmed\" (ожидается ISO-8601, например PT1H или PT30M)",
        )
    }
    if (parsed <= Duration.ZERO) {
        throw IllegalArgumentException("--sync-interval должен быть положительным")
    }
    if (parsed < 1.minutes) {
        throw IllegalArgumentException("минимальный интервал — 1 минута (PT1M)")
    }
    return parsed
}
