package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * [HealthMonitor.start] runs an infinite periodic loop, so it is launched on [backgroundScope] —
 * `advanceUntilIdle()` never returns once a foreground infinite loop is live. Each tick is flushed
 * with [runCurrent] instead, which runs only the work already due at the current virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthMonitorTest {
    private val period = 7.seconds
    private val threshold = 3

    private class ScriptedFileClient(
        private val results: MutableMap<String, MutableList<Boolean>>,
    ) : FileClient(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })) {
        val probed = mutableListOf<String>()

        override suspend fun checkHealth(device: Device): Boolean {
            val fp = requireNotNull(device.fingerprint)
            probed += fp
            val queue = results[fp] ?: return true
            return if (queue.size > 1) queue.removeAt(0) else queue.first()
        }
    }

    private class FakeActiveTransfers(
        initial: Set<String> = emptySet(),
    ) : ActiveTransfers {
        private val _peers = MutableStateFlow(initial)
        override val peers = _peers

        fun setActive(fingerprints: Set<String>) {
            _peers.value = fingerprints
        }
    }

    private fun device(fingerprint: String, port: Int = 5000) =
        Device(name = fingerprint, host = "10.0.0.1", port = port, fingerprint = fingerprint)

    private fun monitor(
        store: DiscoveredDevicesStore,
        client: FileClient,
        activeTransfers: ActiveTransfers = FakeActiveTransfers(),
    ) = HealthMonitor(
        store = store,
        fileClient = client,
        activeTransfers = activeTransfers,
        period = period,
        failureThreshold = threshold,
    )

    @Test
    fun `peer removed after exactly K consecutive failures`() = runTest(UnconfinedTestDispatcher()) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val m = monitor(store, client)

        m.start(backgroundScope)
        runCurrent()
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "must survive after 1 failure")

        advanceTimeBy(period)
        runCurrent()
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "must survive after 2 failures")

        advanceTimeBy(period)
        runCurrent()
        assertTrue(
            store.devices.value.none { it.fingerprint == "fp-1" },
            "must be removed after 3rd consecutive failure",
        )

        m.stop()
    }

    @Test
    fun `one success mid-streak resets the counter`() = runTest(UnconfinedTestDispatcher()) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val results = mutableMapOf("fp-1" to mutableListOf(false, false, true))
        val client = ScriptedFileClient(results)
        val m = monitor(store, client)

        m.start(backgroundScope)
        runCurrent()
        advanceTimeBy(period)
        runCurrent()
        // Two failures recorded, then a success on the third tick resets the streak.
        results["fp-1"] = mutableListOf(true)
        advanceTimeBy(period)
        runCurrent()

        results["fp-1"] = mutableListOf(false, false)
        advanceTimeBy(period)
        runCurrent()
        advanceTimeBy(period)
        runCurrent()
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "reset streak needs 3 fresh failures to evict")

        m.stop()
    }

    @Test
    fun `healthy peers are never removed`() = runTest(UnconfinedTestDispatcher()) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf())
        val m = monitor(store, client)

        m.start(backgroundScope)
        repeat(5) {
            advanceTimeBy(period)
            runCurrent()
        }
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" })

        m.stop()
    }

    @Test
    fun `peer with an active transfer is never probed or evicted`() = runTest(UnconfinedTestDispatcher()) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val activeTransfers = FakeActiveTransfers(setOf("fp-1"))
        val m = monitor(store, client, activeTransfers)

        m.start(backgroundScope)
        repeat(5) {
            advanceTimeBy(period)
            runCurrent()
        }
        assertTrue(client.probed.isEmpty(), "excluded peer must never be probed")
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" })

        m.stop()
    }

    @Test
    fun `peer resumes fresh probing once it leaves the active-transfer set`() = runTest(UnconfinedTestDispatcher()) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val activeTransfers = FakeActiveTransfers(setOf("fp-1"))
        val m = monitor(store, client, activeTransfers)

        m.start(backgroundScope)
        advanceTimeBy(period)
        runCurrent()
        activeTransfers.setActive(emptySet())

        advanceTimeBy(period)
        runCurrent()
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "must survive after 1st failure post-resume")

        advanceTimeBy(period)
        runCurrent()
        advanceTimeBy(period)
        runCurrent()
        assertTrue(
            store.devices.value.none { it.fingerprint == "fp-1" },
            "must evict after 3 fresh failures post-resume",
        )

        m.stop()
    }

    @Test
    fun `refreshNow triggers an out-of-cadence probe`() = runTest(UnconfinedTestDispatcher()) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf())
        val m = monitor(store, client)

        m.start(backgroundScope)
        runCurrent()
        val probesAfterStart = client.probed.size

        m.refreshNow()
        runCurrent()
        assertEquals(probesAfterStart + 1, client.probed.size, "refreshNow must probe once, out of cadence")

        m.stop()
    }

    @Test
    fun `stop halts probing with no further removals`() = runTest(UnconfinedTestDispatcher()) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val m = monitor(store, client)

        m.start(backgroundScope)
        runCurrent()
        m.stop()

        repeat(5) {
            advanceTimeBy(period)
            runCurrent()
        }
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "no probing after stop — peer must survive")

        m.stop()
    }

    @Test
    fun `removal calls store removeByFingerprint and the peer leaves the store`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        store.upsert(device("fp-2"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val m = monitor(store, client)

        m.start(backgroundScope)
        repeat(threshold) {
            advanceTimeBy(period)
            runCurrent()
        }

        val remaining = store.devices.value.map { it.fingerprint }
        assertEquals(listOf("fp-2"), remaining)

        m.stop()
    }
}
