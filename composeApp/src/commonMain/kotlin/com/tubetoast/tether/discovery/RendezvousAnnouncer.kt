package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.InfoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class RendezvousAnnouncer(
    private val store: DiscoveredDevicesStore,
    private val client: FileClient,
    private val ownInfo: () -> InfoDto,
) {
    @Volatile private var collectJob: Job? = null
    private val ackedIds = MutableStateFlow(emptySet<String>())

    fun start(scope: CoroutineScope) {
        if (collectJob != null) return
        collectJob = scope.launch {
            store.devices.collect { devices ->
                val info = ownInfo()
                for (device in devices) {
                    if (device.id in ackedIds.value) continue
                    val ok = client.sendHello(device, info)
                    if (ok) ackedIds.update { it + device.id }
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        ackedIds.value = emptySet()
    }
}
