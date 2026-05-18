package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNameStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class DeviceNameRepublisher(
    private val store: DeviceNameStore,
    private val discovery: DeviceDiscovery,
) {
    @Volatile private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            store.name
                .distinctUntilChanged()
                .drop(1)
                .collect { discovery.republish(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
