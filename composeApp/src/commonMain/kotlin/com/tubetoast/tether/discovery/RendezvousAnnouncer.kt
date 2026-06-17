package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.util.ScopedJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.debug
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "RendezvousAnnouncer")

class RendezvousAnnouncer(
    private val store: DiscoveredDevicesStore,
    private val client: FileClient,
    private val selfAnnouncementProvider: SelfAnnouncementProvider,
) {
    private val lifecycle = ScopedJob()
    private val acknowledgedKeys = MutableStateFlow(emptySet<String>())

    fun start(scope: CoroutineScope) {
        lifecycle.start(scope) {
            store.devices.collect { devices ->
                val info = selfAnnouncementProvider.get()
                for (device in devices) {
                    val key = peerKey(device)
                    if (key in acknowledgedKeys.value) continue
                    log.debug { "announce → ${device.id}" }
                    val ok = client.sendHello(device, info)
                    if (ok) {
                        acknowledgedKeys.update { it + key }
                        store.touch(device.fingerprint)
                    }
                }
            }
        }
    }

    fun stop() {
        lifecycle.stop()
        acknowledgedKeys.value = emptySet()
    }

    private fun peerKey(device: Device): String = device.fingerprint ?: device.id
}
