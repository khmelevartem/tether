package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

expect class MdnsDiscovery : DeviceDiscovery, CanonicalNameSource {
    override val discoveredDevices: StateFlow<List<Device>>

    /**
     * The mDNS-canonical name assigned by the platform after the publish/register callback fires.
     * Null until the callback fires and reset to null on [start]/[republish] until the callback
     * re-fires. The platform may suffix the base name (e.g. "(2)") when multiple services share it.
     * [DefaultSelfAnnouncementProvider] waits briefly for a non-null value before falling back to
     * the configured name.
     */
    override val ownPublishedName: StateFlow<String?>

    override suspend fun start(deviceName: String, port: Int)

    override suspend fun stop()

    override suspend fun republish(name: String)
}
