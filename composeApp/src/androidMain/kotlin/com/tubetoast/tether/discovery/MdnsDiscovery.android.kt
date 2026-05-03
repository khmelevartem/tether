package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MdnsDiscovery actual constructor() {

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    actual fun start(deviceName: String, port: Int) {
        // TODO: NsdManager — register "_tether._tcp." and start discovery
    }

    actual fun stop() {
        // TODO: unregister NSD service and stop listener
        _discoveredDevices.value = emptyList()
    }
}
