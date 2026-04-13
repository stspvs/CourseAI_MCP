package ru.courseai.currencywatch.shared

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

actual class Scheduler actual constructor(private val scope: CoroutineScope) {
    actual fun startPeriodic(interval: Duration, block: suspend () -> Unit): Job {
        val job = Job()
        job.complete()
        return job
    }
}
