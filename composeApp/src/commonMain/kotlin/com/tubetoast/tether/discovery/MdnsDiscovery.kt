package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.Flow

expect class MdnsDiscovery() {
    val discoveredDevices: Flow<List<Device>>
    fun start(deviceName: String, port: Int)
    fun stop()
}
