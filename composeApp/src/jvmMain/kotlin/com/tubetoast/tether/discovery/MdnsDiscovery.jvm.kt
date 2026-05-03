package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MdnsDiscovery actual constructor() {

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    actual fun start(deviceName: String, port: Int) {
        // TODO: val jmdns = JmDNS.create()
        // TODO: jmdns.registerService(ServiceInfo.create("_tether._tcp.local.", deviceName, port, ""))
        // TODO: jmdns.addServiceListener("_tether._tcp.local.", listener)
    }

    actual fun stop() {
        // TODO: jmdns.unregisterAllServices(); jmdns.close()
        _discoveredDevices.value = emptyList()
    }
}
