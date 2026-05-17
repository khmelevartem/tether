package com.tubetoast.tether.network

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

interface TransferActivityTracker {
    suspend fun <T> withActiveTransfer(block: suspend () -> T): T

    fun releaseAll()
}

/**
 * State transitions (`enter`/`exit`/`releaseAll`) run as CAS retry loops: read the current
 * state, compute the next one, and try to swap atomically. If another thread won the swap
 * between read and write, the loop retries with the fresh state. This is a standard
 * lock-free pattern — `synchronized` is unavailable in commonMain, and a coroutine `Mutex`
 * can't guard `releaseAll` (non-suspend, called from `onDestroy`). The `held` flag inside
 * `State` makes both `onFirstEnter` and `onLastExit` fire exactly once per holding period
 * regardless of how transitions interleave.
 */
@OptIn(ExperimentalAtomicApi::class)
class DefaultTransferActivityTracker(
    private val onFirstEnter: () -> Unit = {},
    private val onLastExit: () -> Unit = {},
) : TransferActivityTracker {
    private data class State(
        val count: Int,
        val held: Boolean,
    )

    private val state = AtomicReference(State(0, false))

    override suspend fun <T> withActiveTransfer(block: suspend () -> T): T {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    override fun releaseAll() {
        while (true) {
            val old = state.load()
            if (state.compareAndSet(old, State(0, false))) {
                if (old.held) onLastExit()
                return
            }
        }
    }

    private fun enter() {
        while (true) {
            val old = state.load()
            val acquireNow = old.count == 0 && !old.held
            val new = State(old.count + 1, if (acquireNow) true else old.held)
            if (state.compareAndSet(old, new)) {
                if (acquireNow) onFirstEnter()
                return
            }
        }
    }

    private fun exit() {
        while (true) {
            val old = state.load()
            val newCount = if (old.count > 0) old.count - 1 else 0
            val releaseNow = newCount == 0 && old.held
            val new = State(newCount, if (releaseNow) false else old.held)
            if (state.compareAndSet(old, new)) {
                if (releaseNow) onLastExit()
                return
            }
        }
    }
}
