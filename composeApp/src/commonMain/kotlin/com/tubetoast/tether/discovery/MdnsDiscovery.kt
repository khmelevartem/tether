package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

expect class MdnsDiscovery : DeviceDiscovery {
    override val discoveredDevices: StateFlow<List<Device>>

    override suspend fun start(deviceName: String, port: Int)

    override suspend fun stop()

    override suspend fun republish(name: String)
}
