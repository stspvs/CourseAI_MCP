package ru.courseai.currencywatch.shared

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

expect class Scheduler(scope: CoroutineScope) {
    fun startPeriodic(interval: Duration, block: suspend () -> Unit): Job
}
