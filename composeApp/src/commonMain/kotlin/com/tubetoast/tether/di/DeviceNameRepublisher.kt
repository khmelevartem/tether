package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.DeviceDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class DeviceNameRepublisher(
    private val store: DeviceNameStore,
    private val discovery: DeviceDiscovery,
) {
    // start() can be called from a coroutine on Dispatchers.IO (Android FGS) while stop() runs
    // on the main thread (onDestroy / onDispose). @Volatile guarantees the assignment in start()
    // is visible to stop() so the launched job cannot escape cancellation.
    @Volatile private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            store.name.drop(1).collect { discovery.republish(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
