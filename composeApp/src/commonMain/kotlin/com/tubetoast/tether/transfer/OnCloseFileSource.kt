@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.tubetoast.tether.transfer

import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Delegates all [FileSource] operations to [inner]; calls [onLastClose] exactly once on the first
 * [close] invocation, regardless of how many times [close] is called. Thread-safe.
 */
internal class OnCloseFileSource(
    private val inner: FileSource,
    private val onLastClose: () -> Unit,
) : FileSource by inner {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        try {
            inner.close()
        } finally {
            onLastClose()
        }
    }
}
