package com.tubetoast.tether.discovery

import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.identity.EphemeralFingerprintPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// JmDNS registerService is synchronous but JmDNS binds real sockets on real threads.
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class MdnsDiscoveryOwnNameTest {
    private fun discovery(addresses: List<Pair<String, InetAddress>>): MdnsDiscoveryJmdns =
        MdnsDiscoveryJmdns(
            DiscoveredDevicesStore(),
            Dispatchers.IO,
            DeviceIdentityStore(EphemeralFingerprintPersistence()),
            FakeNetworkInterfaceProvider(addresses),
        )

    @Test
    fun `ownPublishedName is null before start`() = runBlocking {
        val d = discovery(emptyList())
        assertNull(d.ownPublishedName.value, "must be null before any start")
    }

    @Test
    fun `ownPublishedName is set after registration on loopback`(): Unit = runBlocking {
        val addr = InetAddress.getByName("127.0.0.1")
        val d = discovery(listOf("lo0" to addr))
        try {
            d.start("TestInstance", 29900)
            // JmDNS registerService returns synchronously with the locally-deduplicated name
            // (resolved against its in-memory cache and same-process registrations, not live
            // network conflicts); it is assigned inside the locked start block, so it is visible
            // immediately after.
            assertNotNull(d.ownPublishedName.value, "ownPublishedName must be set after start with a live address")
        } finally {
            d.stop()
        }
    }

    @Test
    fun `ownPublishedName is null after stop`() = runBlocking {
        val addr = InetAddress.getByName("127.0.0.1")
        val d = discovery(listOf("lo0" to addr))
        d.start("StopTest", 29901)
        d.stop()
        assertNull(d.ownPublishedName.value, "ownPublishedName must be null after stop")
    }

    @Test
    fun `ownPublishedName reflects new name after republish`() = runBlocking {
        val addr = InetAddress.getByName("127.0.0.1")
        val d = discovery(listOf("lo0" to addr))
        try {
            d.start("RepublishTest", 29902)
            assertNotNull(d.ownPublishedName.value, "set after start")
            d.republish("RepublishTestNew")
            assertEquals("RepublishTestNew", d.ownPublishedName.value, "ownPublishedName must reflect the new name after republish")
        } finally {
            d.stop()
        }
    }
}
