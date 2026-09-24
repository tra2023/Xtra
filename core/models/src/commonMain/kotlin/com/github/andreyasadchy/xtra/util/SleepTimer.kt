package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Platform-agnostic sleep timer. Runs [onFinish] on [scope] after the requested
 * duration and tracks the remaining time. Shared by the playback service and UI.
 */
@OptIn(ExperimentalTime::class)
class SleepTimer(private val scope: CoroutineScope) {

    private var job: Job? = null

    var endTime: Long = 0L
        private set

    /** Starts (or restarts, when [durationMillis] > 0) the timer, returning the previous end time. */
    fun set(durationMillis: Long, onFinish: suspend () -> Unit): Long {
        val previous = endTime
        cancel()
        if (durationMillis > 0L) {
            job = scope.launch {
                delay(durationMillis)
                endTime = 0L
                onFinish()
            }
            endTime = Clock.System.now().toEpochMilliseconds() + durationMillis
        }
        return previous
    }

    fun cancel() {
        job?.cancel()
        job = null
        endTime = 0L
    }

    fun timeLeft(): Long = (endTime - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
}
