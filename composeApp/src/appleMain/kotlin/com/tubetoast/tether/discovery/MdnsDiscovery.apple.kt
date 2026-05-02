package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MdnsDiscovery actual constructor() {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    actual fun start(deviceName: String, port: Int) {
        // TODO: NSNetService(domain: "", type: "_tether._tcp.", name: deviceName, port: port)
        // TODO: NSNetServiceBrowser — search for "_tether._tcp."
    }

    actual fun stop() {
        // TODO: stop NSNetServiceBrowser, invalidate NSNetService
        _discoveredDevices.value = emptyList()
    }
}
