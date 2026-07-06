package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.util.ScopedJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop

class DeviceNameRepublisher(
    private val store: DeviceNameStore,
    private val discovery: DeviceDiscovery,
) {
    private val scopedJob = ScopedJob()

    fun start(scope: CoroutineScope) {
        scopedJob.start(scope) {
            store.name.drop(1).collect { discovery.republish(it) }
        }
    }

    fun stop() {
        scopedJob.stop()
    }
}
