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
