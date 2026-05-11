package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

expect class MdnsDiscovery : DeviceDiscovery {
    override val discoveredDevices: StateFlow<List<Device>>

    override fun start(deviceName: String, port: Int)

    override fun stop()
}
