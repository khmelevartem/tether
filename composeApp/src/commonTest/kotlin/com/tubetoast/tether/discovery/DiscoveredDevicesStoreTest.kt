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

    private fun device(name: String, host: String = "1.2.3.4", port: Int = 8080, fingerprint: String = "fp-$name") =
        Device(name = name, host = host, port = port, fingerprint = fingerprint)

    @Test
    fun `upsert with same fingerprint is idempotent`() {
        val d = device("Peer")
        store.upsert(d)
        store.upsert(d)
        assertEquals(listOf(d), store.devices.value)
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
        val a = device("A", port = 8080)
        val b = device("B", port = 8081)
        store.upsert(a)
        assertEquals(1, store.devices.value.size)
        store.upsert(b)
        assertEquals(2, store.devices.value.size)
        store.removeByFingerprint(a.fingerprint!!)
        assertEquals(listOf(b), store.devices.value)
    }

    @Test
    fun `same fingerprint at new address replaces in place`() {
        val old = device("Peer", host = "1.0.0.1", fingerprint = "fp1")
        val fresh = device("Peer", host = "1.0.0.2", fingerprint = "fp1")
        store.upsert(old)
        store.upsert(fresh)
        assertEquals(listOf(fresh), store.devices.value)
    }

    @Test
    fun `upsert preserves entries with different fingerprints`() {
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

    // Rename storm: peer keeps fingerprint, name changes — collapses to one entry, latest name wins.
    @Test
    fun `same fingerprint different name collapses to one entry with latest name`() {
        store.upsert(device("Peer", fingerprint = "fp1"))
        store.upsert(device("Peer (2)", fingerprint = "fp1"))
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "Peer (2)",
            store.devices.value
                .first()
                .name,
        )
    }

    // Two physically distinct peers must not collapse, even if they transiently share an address.
    @Test
    fun `two entries with distinct fingerprints at same host-port coexist`() {
        store.upsert(device("A", host = "1.2.3.4", port = 8080, fingerprint = "fp1"))
        store.upsert(device("B", host = "1.2.3.4", port = 8080, fingerprint = "fp2"))
        assertEquals(2, store.devices.value.size)
        val fingerprints = store.devices.value
            .map { it.fingerprint }
            .toSet()
        assertEquals(setOf("fp1", "fp2"), fingerprints)
    }

    @Test
    fun `removeByFingerprint evicts renamed entry by fingerprint`() {
        store.upsert(device("A", fingerprint = "fp1"))
        store.upsert(device("A (2)", fingerprint = "fp1"))
        assertEquals(1, store.devices.value.size)
        store.removeByFingerprint("fp1")
        assertTrue(store.devices.value.isEmpty())
    }

    @Test
    fun `removeByFingerprint is a no-op when no entry matches`() {
        store.upsert(device("A", fingerprint = "fp1"))
        store.removeByFingerprint("fp-unknown")
        assertEquals(1, store.devices.value.size)
    }
}
