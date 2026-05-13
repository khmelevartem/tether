package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveredDevicesStoreTest {
    private val store = DiscoveredDevicesStore()

    private fun device(id: String, name: String = id) =
        Device(id = id, name = name, host = "1.2.3.4", port = 8080)

    @Test
    fun `upsert replaces by id`() {
        val original = device("a@1.2.3.4:8080")
        val updated = original.copy(host = "5.6.7.8")
        store.upsert(original)
        store.upsert(updated)
        assertEquals(listOf(updated), store.devices.value)
    }

    @Test
    fun `removeById removes correct entry`() {
        store.upsert(device("a@1.2.3.4:1"))
        store.upsert(device("b@1.2.3.4:2"))
        store.removeById("a@1.2.3.4:1")
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "b@1.2.3.4:2",
            store.devices.value
                .single()
                .id,
        )
    }

    @Test
    fun `removeByName removes all entries with that name`() {
        store.upsert(device("n@1.2.3.4:1", name = "Peer"))
        store.upsert(device("n@5.6.7.8:1", name = "Peer"))
        store.upsert(device("n@1.2.3.4:2", name = "Other"))
        store.removeByName("Peer")
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "Other",
            store.devices.value
                .single()
                .name,
        )
    }

    @Test
    fun `clear removes all entries`() {
        store.upsert(device("a@1.2.3.4:1"))
        store.upsert(device("b@1.2.3.4:2"))
        store.clear()
        assertTrue(store.devices.value.isEmpty())
    }

    @Test
    fun `devices StateFlow value reflects each mutation`() = runTest {
        assertEquals(emptyList(), store.devices.value)
        store.upsert(device("a@1.2.3.4:1"))
        assertEquals(1, store.devices.value.size)
        store.upsert(device("b@1.2.3.4:2"))
        assertEquals(2, store.devices.value.size)
        store.removeById("a@1.2.3.4:1")
        assertEquals(1, store.devices.value.size)
        assertEquals(
            "b@1.2.3.4:2",
            store.devices.value
                .single()
                .id,
        )
    }

    @Test
    fun `three different ids with same name all present after upserts`() {
        store.upsert(device("a@1.0.0.1:80", name = "Peer"))
        store.upsert(device("b@1.0.0.2:80", name = "Peer"))
        store.upsert(device("c@1.0.0.3:80", name = "Peer"))
        assertEquals(3, store.devices.value.size)
        assertTrue(store.devices.value.all { it.name == "Peer" })
    }

    @Test
    fun `removeByName on missing name leaves store unchanged`() {
        store.upsert(device("a@1.2.3.4:1"))
        val before = store.devices.value
        store.removeByName("nonexistent")
        assertEquals(before, store.devices.value)
    }

    @Test
    fun `insertion order preserved — upsert A B C yields A B C`() {
        val a = device("a@1.2.3.4:1", name = "A")
        val b = device("b@1.2.3.4:2", name = "B")
        val c = device("c@1.2.3.4:3", name = "C")
        store.upsert(a)
        store.upsert(b)
        store.upsert(c)
        assertEquals(listOf(a, b, c), store.devices.value)
    }

    @Test
    fun `upsertByName replaces existing entry by name`() {
        val original = device("a@1.2.3.4:8080", name = "Peer")
        store.upsert(original)
        val updated = device("a@5.6.7.8:8080", name = "Peer")
        store.upsertByName(updated)
        assertEquals(listOf(updated), store.devices.value)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `upsertByName produces single emission when replacing`() = runTest(UnconfinedTestDispatcher()) {
        val original = device("a@1.2.3.4:8080", name = "Peer")
        store.upsert(original)

        val snapshots = mutableListOf<List<Device>>()
        val collector = launch { store.devices.collect { snapshots.add(it) } }
        val before = snapshots.size

        store.upsertByName(device("a@5.6.7.8:8080", name = "Peer"))
        collector.cancel()

        assertEquals(1, snapshots.size - before, "expected exactly one emission from upsertByName")
    }

    @Test
    fun `upsertByName adds when name absent`() {
        val device = device("a@1.2.3.4:8080", name = "Peer")
        store.upsertByName(device)
        assertEquals(listOf(device), store.devices.value)
    }

    @Test
    fun `upsertByName preserves position when replacing existing entry by name`() {
        val a = device("a@1.2.3.4:1", name = "A")
        val b = device("b@1.2.3.4:2", name = "B")
        val c = device("c@1.2.3.4:3", name = "C")
        store.upsertByName(a)
        store.upsertByName(b)
        store.upsertByName(c)
        val bUpdated = device("b@5.6.7.8:2", name = "B")
        store.upsertByName(bUpdated)
        assertEquals(listOf(a, bUpdated, c), store.devices.value)
    }

    @Test
    fun `concurrent upserts — all N entries land with no lost updates`() = runTest {
        val n = 50
        val devices = (1..n).map { device("id$it@1.2.3.4:$it", name = "Peer$it") }
        devices
            .map { d ->
                async(Dispatchers.Default) { store.upsert(d) }
            }.awaitAll()
        assertEquals(n, store.devices.value.size)
        val ids = store.devices.value
            .map { it.id }
            .toSet()
        assertEquals(devices.map { it.id }.toSet(), ids)
    }
}
