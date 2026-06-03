package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.PeerAnnouncement
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

interface SelfAnnouncementProvider {
    suspend fun get(): PeerAnnouncement
}

// On async-publish platforms (macOS/Bonjour) the canonical name arrives via a callback that fires
// after the coroutine that calls get() is already running. Two seconds is generous enough for the
// callback to fire in the normal case; the fallback protects against a pathologically late or absent
// callback.
private const val CANONICAL_NAME_WAIT_MS = 2_000L

class DefaultSelfAnnouncementProvider(
    private val nameStore: DeviceNameStore,
    private val fileServer: FileServer,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val ownDeviceType: DeviceType,
    private val canonicalNameSource: CanonicalNameSource,
) : SelfAnnouncementProvider {
    override suspend fun get() = PeerAnnouncement(
        alias = withTimeoutOrNull(CANONICAL_NAME_WAIT_MS) {
            canonicalNameSource.ownPublishedName.filterNotNull().first()
        } ?: nameStore.name.first(),
        fingerprint = deviceIdentityStore.getOrCreate(),
        port = fileServer.port,
        deviceType = ownDeviceType,
    )
}
