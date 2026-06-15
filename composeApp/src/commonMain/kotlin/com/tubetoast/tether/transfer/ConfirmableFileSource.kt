@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.tubetoast.tether.transfer

import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Decorates [inner] with a delivery-confirmation callback. [onDeliveredConfirmed] fires exactly
 * once, the first time the sender confirms this source was delivered (HTTP-200 ack). It never
 * fires on failure, cancel, or drop, and is independent of [close] (per-attempt resource release).
 * Thread-safe; idempotent across retries.
 */
internal class ConfirmableFileSource(
    private val inner: FileSource,
    private val onDeliveredConfirmed: () -> Unit,
) : FileSource by inner {
    private val confirmed = AtomicBoolean(false)

    fun confirmDelivered() {
        if (!confirmed.compareAndSet(expectedValue = false, newValue = true)) return
        onDeliveredConfirmed()
    }
}
