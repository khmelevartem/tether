package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.InfoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class RendezvousAnnouncer(
    private val store: DiscoveredDevicesStore,
    private val client: FileClient,
    private val ownInfo: () -> InfoDto,
) {
    @Volatile private var collectJob: Job? = null
    private val ackedIds = mutableSetOf<String>()

    fun start(scope: CoroutineScope) {
        if (collectJob != null) return
        collectJob = scope.launch {
            store.devices.collect { devices ->
                val info = ownInfo()
                for (device in devices) {
                    if (device.id in ackedIds) continue
                    val ok = client.sendHello(device, info)
                    if (ok) ackedIds += device.id
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        ackedIds.clear()
    }
}
