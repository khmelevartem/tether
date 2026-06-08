package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

actual class MdnsDiscovery(
    private val delegate: DeviceDiscovery,
) : DeviceDiscovery {
    actual override val discoveredDevices: StateFlow<List<Device>> get() = delegate.discoveredDevices

    actual override suspend fun start(deviceName: String, port: Int) = delegate.start(deviceName, port)

    actual override suspend fun stop() = delegate.stop()

    actual override suspend fun republish(name: String) = delegate.republish(name)
}
