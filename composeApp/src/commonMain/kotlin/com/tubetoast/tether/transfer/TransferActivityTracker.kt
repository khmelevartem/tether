package com.tubetoast.tether.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface TransferActivityTracker {
    val active: StateFlow<Boolean>

    suspend fun <T> withActiveTransfer(block: suspend () -> T): T

    fun releaseAll()
}

/** Default for callers that do not surface transfer activity; [active] is permanently `false`. */
object NoOpTransferActivityTracker : TransferActivityTracker {
    override val active: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun <T> withActiveTransfer(block: suspend () -> T): T = block()

    override fun releaseAll() = Unit
}

/**
 * State transitions (`enter`/`exit`/`releaseAll`) run as CAS retry loops over `atomicState`:
 * read the current state, compute the next one, and try to swap atomically. If another thread
 * won the swap between read and write, the loop retries with the fresh state.
 *
 * `active` is a derived projection of `atomicState.held`, so it cannot desync from the committed
 * state — `atomicState` is the single linearization point. Callbacks (`onFirstEnter`/`onLastExit`)
 * fire once per held↔unheld edge, gated on the winning CAS.
 *
 * Lock-free — `synchronized` is unavailable in commonMain, and a coroutine `Mutex` cannot guard
 * `releaseAll` (non-suspend, called from `onDestroy`).
 */
class DefaultTransferActivityTracker(
    private val scope: CoroutineScope,
    private val onFirstEnter: () -> Unit = {},
    private val onLastExit: () -> Unit = {},
) : TransferActivityTracker {
    private data class State(
        val count: Int,
        val held: Boolean,
    )

    private val atomicState = MutableStateFlow(State(0, false))

    override val active: StateFlow<Boolean> = atomicState
        .map { it.held }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = false)

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
            val old = atomicState.value
            if (atomicState.compareAndSet(old, State(0, false))) {
                if (old.held) {
                    onLastExit()
                }
                return
            }
        }
    }

    private fun enter() {
        while (true) {
            val old = atomicState.value
            val acquireNow = old.count == 0 && !old.held
            val new = State(old.count + 1, if (acquireNow) true else old.held)
            if (atomicState.compareAndSet(old, new)) {
                if (acquireNow) {
                    onFirstEnter()
                }
                return
            }
        }
    }

    private fun exit() {
        while (true) {
            val old = atomicState.value
            val newCount = if (old.count > 0) old.count - 1 else 0
            val releaseNow = newCount == 0 && old.held
            val new = State(newCount, if (releaseNow) false else old.held)
            if (atomicState.compareAndSet(old, new)) {
                if (releaseNow) {
                    onLastExit()
                }
                return
            }
        }
    }
}
