package com.tubetoast.tether.network

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

interface TransferActivityTracker {
    suspend fun <T> withActiveTransfer(block: suspend () -> T): T

    fun releaseAll()
}

@OptIn(ExperimentalAtomicApi::class)
class DefaultTransferActivityTracker(
    private val onFirstEnter: () -> Unit = {},
    private val onLastExit: () -> Unit = {},
) : TransferActivityTracker {
    private val count = AtomicInt(0)

    override suspend fun <T> withActiveTransfer(block: suspend () -> T): T {
        if (count.fetchAndAdd(1) == 0) onFirstEnter()
        try {
            return block()
        } finally {
            if (decrementIfPositive() == 0) onLastExit()
        }
    }

    override fun releaseAll() {
        if (count.exchange(0) > 0) onLastExit()
    }

    private fun decrementIfPositive(): Int {
        while (true) {
            val cur = count.load()
            if (cur == 0) return -1
            if (count.compareAndSet(cur, cur - 1)) return cur - 1
        }
    }
}
