package com.tubetoast.tether.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Wraps a single background [Job] with guarded start/stop semantics.
 * Double-[start] while active is a no-op; [start] after [stop] re-launches the block.
 */
class ScopedJob {
    @Volatile private var job: Job? = null

    fun start(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch(block = block)
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
