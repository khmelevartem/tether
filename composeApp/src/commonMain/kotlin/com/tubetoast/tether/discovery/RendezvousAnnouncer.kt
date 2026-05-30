package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class RendezvousAnnouncer(
    private val store: DiscoveredDevicesStore,
    private val client: FileClient,
    private val selfAnnouncementProvider: SelfAnnouncementProvider,
) {
    @Volatile private var collectJob: Job? = null
    private val acknowledgedIds = MutableStateFlow(emptySet<String>())

    fun start(scope: CoroutineScope) {
        if (collectJob != null) return
        collectJob = scope.launch {
            store.devices.collect { devices ->
                val info = selfAnnouncementProvider.get()
                for (device in devices) {
                    if (device.id in acknowledgedIds.value) continue
                    val ok = client.sendHello(device, info)
                    if (ok) acknowledgedIds.update { it + device.id }
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        acknowledgedIds.value = emptySet()
    }
}
