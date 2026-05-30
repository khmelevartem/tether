package com.tubetoast.tether.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// JmDNS binds real sockets and delivers callbacks on real threads outside our CoroutineScope.
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class MdnsDiscoveryMultiInterfaceTest {
    private fun fakeAddr(host: String): InetAddress =
        InetAddress.getByName(host)

    private fun discovery(
        fingerprint: String = "",
        addresses: List<Pair<String, InetAddress>>,
    ): MdnsDiscoveryJmdns {
        val store = DiscoveredDevicesStore()
        val fakeProvider = FakeNetworkInterfaceProvider(addresses)
        return MdnsDiscoveryJmdns(store, Dispatchers.IO, fingerprint, fakeProvider)
    }

    @Test
    fun `creates one JmDNS instance per fake address`() = runBlocking {
        val addr1 = fakeAddr("127.0.0.1")
        val addr2 = fakeAddr("127.0.0.2")
        val d = discovery(
            addresses = listOf("lo0" to addr1, "lo1" to addr2),
        )
        try {
            d.start("MultiA", 29100)
            // Allow instances to be created before checking.
            withTimeout(5_000) {
                while (d.instanceCount < 2) delay(50)
            }
            assertEquals(2, d.instanceCount)
        } finally {
            d.stop()
        }
    }

    @Test
    fun `self-fingerprint suppress filters out own announce`() = runBlocking {
        val addr = fakeAddr("127.0.0.1")
        val d1 = discovery(fingerprint = "fp-x", addresses = listOf("lo0" to addr))
        val d2 = discovery(fingerprint = "fp-x", addresses = listOf("lo0" to addr))
        try {
            d1.start("SelfSuppressA", 29200)
            d2.start("SelfSuppressB", 29201)
            delay(3_000)
            assertTrue(
                d1.discoveredDevices.value.none { it.name == "SelfSuppressA" },
                "d1 must not discover itself",
            )
            assertTrue(
                d2.discoveredDevices.value.none { it.name == "SelfSuppressB" },
                "d2 must not discover itself",
            )
        } finally {
            d1.stop()
            d2.stop()
        }
    }

    @Test
    fun `stop tears down all JmDNS instances`() = runBlocking {
        val addr1 = fakeAddr("127.0.0.1")
        val addr2 = fakeAddr("127.0.0.2")
        val d = discovery(addresses = listOf("lo0" to addr1, "lo1" to addr2))
        d.start("TeardownTest", 29300)
        withTimeout(5_000) { while (d.instanceCount < 2) delay(50) }
        d.stop()
        assertEquals(0, d.instanceCount, "all JmDNS instances must be torn down after stop()")
    }

    @Test
    fun `diffInterfaces brings up new interface`() = runBlocking {
        val addrA = fakeAddr("127.0.0.1")
        val addrB = fakeAddr("127.0.0.2")
        val fakeProvider = FakeNetworkInterfaceProvider(listOf("lo0" to addrA))
        val store = DiscoveredDevicesStore()
        val d = MdnsDiscoveryJmdns(store, Dispatchers.IO, "", fakeProvider)
        try {
            d.start("DiffUpTest", 29400)
            withTimeout(5_000) { while (d.instanceCount < 1) delay(50) }
            assertEquals(1, d.instanceCount)

            fakeProvider.addresses = listOf("lo0" to addrA, "lo1" to addrB)
            d.diffInterfaces()

            assertEquals(2, d.instanceCount, "new interface must be brought up by diffInterfaces")
        } finally {
            d.stop()
        }
    }

    @Test
    fun `diffInterfaces tears down removed interface`() = runBlocking {
        val addrA = fakeAddr("127.0.0.1")
        val addrB = fakeAddr("127.0.0.2")
        val fakeProvider = FakeNetworkInterfaceProvider(listOf("lo0" to addrA, "lo1" to addrB))
        val store = DiscoveredDevicesStore()
        val d = MdnsDiscoveryJmdns(store, Dispatchers.IO, "", fakeProvider)
        try {
            d.start("DiffDownTest", 29500)
            withTimeout(5_000) { while (d.instanceCount < 2) delay(50) }
            assertEquals(2, d.instanceCount)

            fakeProvider.addresses = listOf("lo0" to addrA)
            d.diffInterfaces()

            assertEquals(1, d.instanceCount, "removed interface must be torn down by diffInterfaces")
        } finally {
            d.stop()
        }
    }
}

internal class FakeNetworkInterfaceProvider(
    var addresses: List<Pair<String, InetAddress>>,
) : NetworkInterfaceProvider {
    override fun bindAddresses(): List<Pair<String, InetAddress>> = addresses
}

private val MdnsDiscoveryJmdns.instanceCount: Int
    get() = synchronized(this) { instances.size }
