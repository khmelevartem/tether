@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class DiscoveredDevicesStoreTest {
    // staleGrace=ZERO so removeByName always evicts in structural tests that predate idle-expiry.
    private val store = DiscoveredDevicesStore(staleGrace = Duration.ZERO)

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
    fun `removeByName evicts the entry whose current name matches`() {
        store.upsert(device("Peer", host = "1.0.0.1", fingerprint = "fpPeer"))
        store.upsert(device("Other", host = "1.0.0.2", fingerprint = "fpOther"))
        store.removeByName("Peer")
        assertEquals(
            listOf(device("Other", host = "1.0.0.2", fingerprint = "fpOther")),
            store.devices.value,
        )
    }

    @Test
    fun `removeByName on missing name is a no-op`() {
        store.upsert(device("a"))
        store.removeByName("nope")
        assertEquals(1, store.devices.value.size)
    }

    // The store collapses multi-rename announces to one canonical entry (Rule 1), so a
    // removeByName for any intermediate (already superseded) name must be a no-op for the
    // live entry. This is the store-level mirror of BonjourStateTest's regression case.
    @Test
    fun `removeByName for a stale rename name does not evict the live canonical entry`() {
        store.upsert(device("Peer", fingerprint = "fpA"))
        store.upsert(device("Peer (2)", fingerprint = "fpA"))
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "Peer (2)",
            store.devices.value
                .single()
                .name,
        )

        store.removeByName("Peer")
        assertEquals(1, store.devices.value.size, "stale-name lookup must not find a match")
        assertEquals(
            "Peer (2)",
            store.devices.value
                .single()
                .name,
        )

        store.removeByName("Peer (2)")
        assertTrue(store.devices.value.isEmpty())
    }

    @Test
    fun `removeByFingerprint is a no-op when no entry matches`() {
        store.upsert(device("A", fingerprint = "fp1"))
        store.removeByFingerprint("fp-unknown")
        assertEquals(1, store.devices.value.size)
    }

    // ── idle-expiry (DoD) ──────────────────────────────────────────────────────

    @Test
    fun `hello-only peer is idle-expired after idleWindow`() {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(idleWindow = 5.minutes, timeSource = clock)
        timedStore.upsert(device("Peer", fingerprint = "fp1"))
        assertEquals(1, timedStore.devices.value.size)

        clock += 5.minutes
        timedStore.evictIdle()

        assertTrue(timedStore.devices.value.isEmpty())
    }

    @Test
    fun `touch before idleWindow keeps peer alive past what would otherwise expire it`() {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(idleWindow = 5.minutes, timeSource = clock)
        timedStore.upsert(device("Peer", fingerprint = "fp1"))

        clock += 4.minutes
        timedStore.touch("fp1")

        // Without touch the peer would expire at t=5m; with touch it expires at t=4m+5m=9m.
        clock += 4.minutes + 59.seconds
        timedStore.evictIdle()
        assertEquals(1, timedStore.devices.value.size, "peer should still be alive")

        clock += 1.seconds
        timedStore.evictIdle()
        assertTrue(timedStore.devices.value.isEmpty())
    }

    @Test
    fun `fresh hello upsert is NOT displaced by subsequent removeByName within staleGrace`() {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(staleGrace = 10.seconds, timeSource = clock)
        timedStore.upsert(device("Peer", fingerprint = "fp1"))

        // serviceLost arrives almost immediately — peer was re-proven by fresh upsert above
        clock += 5.seconds
        timedStore.removeByName("Peer")

        assertEquals(1, timedStore.devices.value.size, "fresh upsert must survive serviceLost within staleGrace")
    }

    @Test
    fun `stale entry IS evicted by removeByName when last-seen exceeds staleGrace`() {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(staleGrace = 10.seconds, timeSource = clock)
        timedStore.upsert(device("Peer", fingerprint = "fp1"))

        clock += 11.seconds
        timedStore.removeByName("Peer")

        assertTrue(timedStore.devices.value.isEmpty(), "genuinely stale entry must be evicted by removeByName")
    }

    @Test
    fun `janitor loop driven by virtual time expires idle peers`() = runTest {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(
            idleWindow = 5.minutes,
            sweepInterval = 30.seconds,
            timeSource = clock,
        )
        timedStore.upsert(device("Peer", fingerprint = "fp1"))
        timedStore.start(this)

        // Advance TestTimeSource first so evictIdle sees elapsed marks, then advance virtual
        // time so the janitor's delay fires.
        clock += 5.minutes + 30.seconds
        advanceTimeBy(5.minutes + 30.seconds)

        assertTrue(timedStore.devices.value.isEmpty())
        timedStore.stop()
    }

    // ── Finding-7 edge cases ───────────────────────────────────────────────────

    @Test
    fun `touch for unknown fingerprint does not create a mark and is a no-op`() {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(idleWindow = 5.minutes, timeSource = clock)
        timedStore.touch("ghost-fp")

        // No device was ever added — touch must not plant a mark that evictIdle could act on.
        clock += 5.minutes
        timedStore.evictIdle()

        assertTrue(timedStore.devices.value.isEmpty())
    }

    @Test
    fun `removeByName boundary at exactly staleGrace evicts the entry`() {
        val clock = TestTimeSource()
        // staleGrace guard is elapsedNow() < staleGrace, so at exactly staleGrace the entry IS evicted.
        val timedStore = DiscoveredDevicesStore(staleGrace = 10.seconds, timeSource = clock)
        timedStore.upsert(device("Peer", fingerprint = "fp1"))

        clock += 10.seconds
        timedStore.removeByName("Peer")

        assertTrue(
            timedStore.devices.value.isEmpty(),
            "entry at exactly staleGrace must be evicted (guard is strict <)",
        )
    }

    @Test
    fun `touch within staleGrace protects peer from subsequent removeByName`() {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(staleGrace = 10.seconds, timeSource = clock)
        timedStore.upsert(device("Peer", fingerprint = "fp1"))

        // Advance past staleGrace so the upsert mark is stale, then touch to refresh.
        clock += 11.seconds
        timedStore.touch("fp1")

        // removeByName arrives immediately after touch — touch mark is within staleGrace.
        timedStore.removeByName("Peer")

        assertEquals(1, timedStore.devices.value.size, "touch within staleGrace must protect the peer")
    }

    @Test
    fun `no-fingerprint device is stored but never expired by evictIdle or removeByName`() {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(idleWindow = 5.minutes, staleGrace = 10.seconds, timeSource = clock)
        val noFp = Device(name = "NoFp", host = "1.2.3.4", port = 8080, fingerprint = null)
        timedStore.upsert(noFp)

        assertEquals(1, timedStore.devices.value.size)

        clock += 10.minutes
        timedStore.evictIdle()
        assertEquals(1, timedStore.devices.value.size, "no-fingerprint peer must not be idle-expired")

        timedStore.removeByName("NoFp")
        assertEquals(
            1,
            timedStore.devices.value.size,
            "removeByName without staleGrace guard must not evict no-fingerprint peer",
        )
    }

    @Test
    fun `janitor double-start is a no-op and stop-start cycle re-arms the janitor`() = runTest {
        val clock = TestTimeSource()
        val timedStore = DiscoveredDevicesStore(
            idleWindow = 5.minutes,
            sweepInterval = 30.seconds,
            timeSource = clock,
        )
        timedStore.upsert(device("Peer", fingerprint = "fp1"))
        timedStore.start(this)
        timedStore.start(this) // second start must be a no-op

        clock += 5.minutes + 30.seconds
        advanceTimeBy(5.minutes + 30.seconds)
        assertTrue(timedStore.devices.value.isEmpty(), "janitor must fire exactly once")

        // Re-arm: stop then start should allow the janitor to run again.
        timedStore.stop()
        timedStore.upsert(device("Peer2", fingerprint = "fp2"))
        timedStore.start(this)

        clock += 5.minutes + 30.seconds
        advanceTimeBy(5.minutes + 30.seconds)
        assertTrue(timedStore.devices.value.isEmpty(), "re-armed janitor must expire the new peer")
        timedStore.stop()
    }
}
