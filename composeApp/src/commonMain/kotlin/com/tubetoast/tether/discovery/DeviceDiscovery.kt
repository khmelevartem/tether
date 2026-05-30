package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

interface DeviceDiscovery {
    val discoveredDevices: StateFlow<List<Device>>

    suspend fun start(deviceName: String, port: Int)

    fun stop()

    /** No-op if [start] was never called. */
    fun republish(name: String)
}
