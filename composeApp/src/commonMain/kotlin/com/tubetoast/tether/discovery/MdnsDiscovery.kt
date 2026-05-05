package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

expect class MdnsDiscovery() {
    val discoveredDevices: StateFlow<List<Device>>

    fun start(deviceName: String, port: Int)

    fun stop()
}
