package com.tubetoast.tether.discovery

import com.tubetoast.tether.di.desktopPlatform
import com.tubetoast.tether.identity.DeviceIdentityStore
import java.util.concurrent.atomic.AtomicInteger

private val instanceCounter = AtomicInteger(0)

internal fun uniqueTestPubKey(): ByteArray {
    val id = instanceCounter.incrementAndGet()
    return ByteArray(32).also { buf ->
        buf[0] = (id shr 24).toByte()
        buf[1] = (id shr 16).toByte()
        buf[2] = (id shr 8).toByte()
        buf[3] = id.toByte()
    }
}

internal fun testDiscovery(
    store: DiscoveredDevicesStore = DiscoveredDevicesStore(),
    pubKey: ByteArray = uniqueTestPubKey(),
): MdnsDiscovery {
    val identityStore = DeviceIdentityStore(pubKey)
    return MdnsDiscovery(desktopPlatform.mdnsDelegate(store, identityStore))
}
