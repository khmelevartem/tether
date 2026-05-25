package com.tubetoast.tether.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class LostDebouncer(
    private val scope: CoroutineScope,
    private val debounceMs: Long,
    private val onRemove: (String) -> Unit,
) {
    private val pending = mutableMapOf<String, Job>()

    @Synchronized
    fun scheduleRemoval(name: String) {
        pending[name]?.cancel()
        pending[name] = scope.launch {
            delay(debounceMs)
            synchronized(this@LostDebouncer) { pending.remove(name) }
            onRemove(name)
        }
    }

    @Synchronized
    fun cancelRemoval(name: String) {
        pending.remove(name)?.cancel()
    }

    @Synchronized
    fun cancelAll() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
}
