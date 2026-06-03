package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.PeerAnnouncement
import kotlinx.coroutines.flow.first

interface SelfAnnouncementProvider {
    suspend fun get(): PeerAnnouncement
}

class DefaultSelfAnnouncementProvider(
    private val nameStore: DeviceNameStore,
    private val fileServer: FileServer,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val ownDeviceType: DeviceType,
) : SelfAnnouncementProvider {
    override suspend fun get() = PeerAnnouncement(
        alias = nameStore.name.first(),
        fingerprint = deviceIdentityStore.getOrCreate(),
        port = fileServer.port,
        deviceType = ownDeviceType,
    )
}
