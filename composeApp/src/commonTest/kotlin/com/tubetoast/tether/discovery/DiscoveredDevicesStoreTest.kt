package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveredDevicesStoreTest {
    private val store = DiscoveredDevicesStore()

    private fun device(name: String, host: String = "1.2.3.4", port: Int = 8080) =
        Device(name = name, host = host, port = port)

    @Test
    fun `upsert with same device is idempotent`() {
        val d = device("Peer")
        store.upsert(d)
        store.upsert(d)
        assertEquals(listOf(d), store.devices.value)
    }

    @Test
    fun `removeByName removes all entries with matching name`() {
        store.upsert(device("Peer", host = "1.0.0.1"))
        store.upsert(device("Other", host = "1.0.0.2"))
        store.removeByName("Peer")
        assertEquals(listOf(device("Other", host = "1.0.0.2")), store.devices.value)
    }

    @Test
    fun `removeByName on missing name is a no-op`() {
        store.upsert(device("a"))
        store.removeByName("nope")
        assertEquals(1, store.devices.value.size)
    }

    @Test
    fun `clear removes all entries`() {
        store.upsert(device("a"))
        store.upsert(device("b"))
        store.clear()
        assertTrue(store.devices.value.isEmpty())
    }

    @Test
    fun `devices StateFlow value reflects each mutation`() = runTest {
        assertEquals(emptyList(), store.devices.value)
        store.upsert(device("A"))
        assertEquals(1, store.devices.value.size)
        store.upsert(device("B"))
        assertEquals(2, store.devices.value.size)
        store.removeByName("A")
        assertEquals(listOf(device("B")), store.devices.value)
    }

    @Test
    fun `upsert with same name and new address evicts stale entry`() {
        val old = device("Peer", host = "1.0.0.1")
        val fresh = device("Peer", host = "1.0.0.2")
        store.upsert(old)
        store.upsert(fresh)
        assertEquals(listOf(fresh), store.devices.value)
    }

    @Test
    fun `upsert preserves entries with different names`() {
        val a = device("A")
        val b = device("B", port = 81)
        store.upsert(a)
        store.upsert(b)
        assertEquals(listOf(a, b), store.devices.value)
    }

    @Test
    fun `insertion order preserved`() {
        val a = device("A")
        val b = device("B", port = 81)
        val c = device("C", port = 82)
        store.upsert(a)
        store.upsert(b)
        store.upsert(c)
        assertEquals(listOf(a, b, c), store.devices.value)
    }

    @Test
    fun `concurrent upserts — all N entries land with no lost updates`() = runTest {
        val n = 50
        val devices = (1..n).map { device("Peer$it", port = it) }
        devices
            .map { d ->
                async(Dispatchers.Default) { store.upsert(d) }
            }.awaitAll()
        assertEquals(n, store.devices.value.size)
        assertEquals(
            devices.map { it.id }.toSet(),
            store.devices.value
                .map { it.id }
                .toSet(),
        )
    }
}
