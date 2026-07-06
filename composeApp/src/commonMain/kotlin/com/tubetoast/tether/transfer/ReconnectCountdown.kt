package com.tubetoast.tether.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Ticks down from [timeout] in one-second steps, invoking [onTick] each second with the
 * remaining seconds, then [onExpired] when the countdown reaches zero. The countdown can be
 * cancelled via [cancel] — for example, when a reconnection event arrives before expiry.
 */
class ReconnectCountdown(
    private val timeout: Duration,
    private val scope: CoroutineScope,
    private val onTick: suspend (remainingSeconds: Int) -> Unit,
    private val onExpired: suspend () -> Unit = {},
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            val totalSeconds = timeout.inWholeSeconds.toInt()
            for (remaining in totalSeconds downTo 1) {
                onTick(remaining)
                delay(1_000)
            }
            onExpired()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
