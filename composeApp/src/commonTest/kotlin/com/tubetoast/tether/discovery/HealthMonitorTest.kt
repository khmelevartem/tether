package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PeerIdentity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class HealthMonitorTest {
    private val period = 7.seconds
    private val fastPeriod = 2.seconds
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
        initial: Set<PeerIdentity> = emptySet(),
    ) : ActiveTransfers {
        private val _peers = MutableStateFlow(initial)
        override val peers = _peers

        fun setActive(peers: Set<PeerIdentity>) {
            _peers.value = peers
        }
    }

    private fun device(fingerprint: String, port: Int = 5000) =
        Device(name = fingerprint, host = "10.0.0.1", port = port, fingerprint = fingerprint)

    private fun monitor(
        store: DiscoveredDevicesStore,
        client: FileClient,
        activeTransfers: ActiveTransfers = FakeActiveTransfers(),
        probeDispatcher: CoroutineDispatcher,
    ) = HealthMonitor(
        store = store,
        fileClient = client,
        activeTransfers = activeTransfers,
        period = period,
        fastPeriod = fastPeriod,
        failureThreshold = threshold,
        probeDispatcher = probeDispatcher,
    )

    /** [advanceTimeBy] leaves a task scheduled exactly at the target instant unrun; overshoot by 1ms to flush it. */
    private fun TestScope.tick(duration: Duration = 1.milliseconds) = advanceTimeBy(duration + 1.milliseconds)

    @Test
    fun `peer removed after exactly K consecutive failures`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val m = monitor(store, client, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick()
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "must survive after 1 failure")

        tick(fastPeriod)
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "must survive after 2 failures")

        tick(fastPeriod)
        assertTrue(
            store.devices.value.none { it.fingerprint == "fp-1" },
            "must be removed after 3rd consecutive failure",
        )

        m.stop()
    }

    @Test
    fun `one success mid-streak resets the counter`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val results = mutableMapOf("fp-1" to mutableListOf(false, false, true))
        val client = ScriptedFileClient(results)
        val m = monitor(store, client, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick()
        tick(fastPeriod)
        // Two failures recorded, then a success on the third tick resets the streak.
        results["fp-1"] = mutableListOf(true)
        tick(fastPeriod)

        results["fp-1"] = mutableListOf(false, false)
        tick(period)
        tick(fastPeriod)
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "reset streak needs 3 fresh failures to evict")

        m.stop()
    }

    @Test
    fun `healthy peers are never removed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf())
        val m = monitor(store, client, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        repeat(5) {
            tick(period)
        }
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" })

        m.stop()
    }

    @Test
    fun `peer with an active transfer is never probed or evicted`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val activeTransfers = FakeActiveTransfers(setOf(PeerIdentity("fp-1")))
        val m = monitor(store, client, activeTransfers, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        repeat(5) {
            tick(period)
        }
        assertTrue(client.probed.isEmpty(), "excluded peer must never be probed")
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" })

        m.stop()
    }

    @Test
    fun `peer resumes fresh probing once it leaves the active-transfer set`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val activeTransfers = FakeActiveTransfers(setOf(PeerIdentity("fp-1")))
        val m = monitor(store, client, activeTransfers, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick(period)
        activeTransfers.setActive(emptySet())

        tick(period)
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "must survive after 1st failure post-resume")

        tick(fastPeriod)
        tick(fastPeriod)
        assertTrue(
            store.devices.value.none { it.fingerprint == "fp-1" },
            "must evict after 3 fresh failures post-resume",
        )

        m.stop()
    }

    @Test
    fun `counter cleared on exclusion exit requires a full fresh streak to evict`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val activeTransfers = FakeActiveTransfers()
        val m = monitor(store, client, activeTransfers, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick()
        tick(fastPeriod)
        // Two consecutive failures recorded (K-1 of 3) — one more would evict.
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "must survive after 2 failures")

        activeTransfers.setActive(setOf(PeerIdentity("fp-1")))
        // First excluded tick observes the exclusion, prunes the counter, and reverts cadence to `period`.
        tick(fastPeriod)
        tick(period)
        activeTransfers.setActive(emptySet())

        tick(period)
        assertTrue(
            store.devices.value.any { it.fingerprint == "fp-1" },
            "counter must have been cleared on exclusion exit — 1 failure post-resume must not evict",
        )

        tick(fastPeriod)
        tick(fastPeriod)
        assertTrue(
            store.devices.value.none { it.fingerprint == "fp-1" },
            "must evict only after a full fresh streak of 3 failures post-resume",
        )

        m.stop()
    }

    @Test
    fun `stop halts probing with no further removals`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val m = monitor(store, client, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick()
        m.stop()

        repeat(5) {
            tick(period)
        }
        assertTrue(store.devices.value.any { it.fingerprint == "fp-1" }, "no probing after stop — peer must survive")

        m.stop()
    }

    @Test
    fun `removal calls store removeByFingerprint and the peer leaves the store`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        store.upsert(device("fp-2"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val m = monitor(store, client, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick()
        repeat(threshold - 1) {
            tick(fastPeriod)
        }

        val remaining = store.devices.value.map { it.fingerprint }
        assertEquals(listOf("fp-2"), remaining)

        m.stop()
    }

    @Test
    fun `concurrent peers accumulate independent failure streaks`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        store.upsert(device("fp-2"))
        val client = ScriptedFileClient(
            mutableMapOf(
                "fp-1" to mutableListOf(false, false, false),
                "fp-2" to mutableListOf(false, true, false, true, false, true),
            ),
        )
        val m = monitor(store, client, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick()
        repeat(threshold - 1) {
            tick(fastPeriod)
        }

        val remaining = store.devices.value.map { it.fingerprint }
        assertEquals(
            listOf("fp-2"),
            remaining,
            "fp-1 fails every probe and must be evicted after $threshold ticks; " +
                "fp-2 alternates and must remain since its streak keeps resetting",
        )

        m.stop()
    }

    @Test
    fun `peer that starts failing while others are healthy is evicted on the fast cadence`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiscoveredDevicesStore()
        store.upsert(device("fp-1"))
        store.upsert(device("fp-2"))
        val client = ScriptedFileClient(mutableMapOf("fp-1" to mutableListOf(false)))
        val m = monitor(store, client, probeDispatcher = dispatcher)

        m.start(backgroundScope)
        tick()
        tick(fastPeriod)
        tick(fastPeriod)

        val remaining = store.devices.value.map { it.fingerprint }
        assertEquals(
            listOf("fp-2"),
            remaining,
            "adaptive cadence must evict fp-1 within period + 2*fastPeriod, not wait 3 full periods",
        )

        m.stop()
    }
}
