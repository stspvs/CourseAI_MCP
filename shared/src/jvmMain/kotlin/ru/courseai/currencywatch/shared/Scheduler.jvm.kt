package ru.courseai.currencywatch.shared

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ru.courseai.currencywatch.shared.Scheduler")

actual class Scheduler actual constructor(private val scope: CoroutineScope) {
    actual fun startPeriodic(interval: Duration, block: suspend () -> Unit): Job =
        scope.launch {
            while (isActive) {
                try {
                    block()
                } catch (e: Throwable) {
                    log.warn("Периодическая задача завершилась с ошибкой", e)
                }
                delay(interval)
            }
        }
}
