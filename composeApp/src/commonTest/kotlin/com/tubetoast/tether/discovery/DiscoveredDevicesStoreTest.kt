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

    private fun device(name: String, host: String = "1.2.3.4", port: Int = 8080, fingerprint: String? = null) =
        Device(name = name, host = host, port = port, fingerprint = fingerprint)

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
        store.upsert(device("A", port = 8080))
        assertEquals(1, store.devices.value.size)
        store.upsert(device("B", port = 8081))
        assertEquals(2, store.devices.value.size)
        store.removeByName("A")
        assertEquals(listOf(device("B", port = 8081)), store.devices.value)
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

    // Rule 1: same fingerprint, different name/host/port — one entry, latest wins.
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

    // Rule 1: same fingerprint, different name/host — replaces in place (verifies Rule 1, not Rule 4).
    @Test
    fun `same fingerprint different host replaces in place`() {
        store.upsert(device("Peer", host = "1.0.0.1", fingerprint = "fp1"))
        store.upsert(device("Peer (2)", host = "1.0.0.2", fingerprint = "fp1"))
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "1.0.0.2",
            store.devices.value
                .first()
                .host,
        )
    }

    // Rule 2: name-only entry first, then fingerprint-bearing at same host:port with different name — promotes.
    @Test
    fun `fingerprint-bearing entry at same host-port promotes name-only entry`() {
        store.upsert(device("Peer"))
        store.upsert(device("Peer (2)", fingerprint = "fp1"))
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "fp1",
            store.devices.value
                .first()
                .fingerprint,
        )
    }

    // Rule 3: fingerprint-bearing first, then name-only at same host:port — drops incoming.
    @Test
    fun `name-only entry at same host-port is dropped when fingerprint-bearing entry exists`() {
        store.upsert(device("Peer", fingerprint = "fp1"))
        store.upsert(device("Peer (2)"))
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "fp1",
            store.devices.value
                .first()
                .fingerprint,
        )
        assertEquals(
            "Peer",
            store.devices.value
                .first()
                .name,
        )
    }

    // Rule 3: name-only incoming, name-only existing at same host:port — drops incoming.
    @Test
    fun `name-only entry at same host-port is dropped when another name-only entry exists`() {
        store.upsert(device("Peer"))
        store.upsert(device("Peer (2)"))
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "Peer",
            store.devices.value
                .first()
                .name,
        )
    }

    // Distinct fingerprints → two entries.
    @Test
    fun `distinct fingerprints produce two entries`() {
        store.upsert(device("A", fingerprint = "fp1"))
        store.upsert(device("B", port = 81, fingerprint = "fp2"))
        assertEquals(2, store.devices.value.size)
    }

    // Finding 5: distinct fingerprints at same host:port must not collapse.
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
