package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

expect class MdnsDiscovery : DeviceDiscovery {
    override val discoveredDevices: StateFlow<List<Device>>

    /**
     * The mDNS-canonical name assigned by the platform after the publish/register callback fires.
     * Null until the callback fires, and reset to null on [start]/[republish] until the callback re-fires.
     * The platform may suffix a base name with `(2)`, `(3)`, etc. when multiple services share it.
     */
    val ownPublishedName: StateFlow<String?>

    override suspend fun start(deviceName: String, port: Int)

    override suspend fun stop()

    override suspend fun republish(name: String)
}
