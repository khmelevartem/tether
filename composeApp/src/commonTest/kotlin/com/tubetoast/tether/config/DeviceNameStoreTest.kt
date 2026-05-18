package com.tubetoast.tether.config

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceNameStoreTest {
    @Test
    fun `init emits default when persistence is empty`() = runTest {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val name = store.name.first()
        assertTrue(name.isNotEmpty(), "Expected non-empty default, got: '$name'")
    }

    @Test
    fun `init emits persisted value when available`() = runTest {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence("MyDevice"))
        store.init()
        assertEquals("MyDevice", store.name.first())
    }

    @Test
    fun `setName returns trimmed value on success`() = runTest {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val result = store.setName("  Alice  ")
        assertTrue(result.isSuccess)
        assertEquals("Alice", result.getOrThrow())
    }

    @Test
    fun `setName updates flow on success`() = runTest {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        store.setName("Bob")
        assertEquals("Bob", store.name.first())
    }

    @Test
    fun `setName persists value`() = runTest {
        val persistence = InMemoryDeviceNamePersistence(null)
        val store = DeviceNameStore(persistence)
        store.init()
        store.setName("Carol")
        assertEquals("Carol", persistence.read())
    }

    @Test
    fun `setName failure does not mutate flow`() = runTest {
        val persistence = InMemoryDeviceNamePersistence("original")
        val store = DeviceNameStore(persistence)
        store.init()
        val before = store.name.first()
        val result = store.setName("  ")
        assertTrue(result.isFailure)
        assertEquals(before, store.name.first())
    }

    @Test
    fun `setName failure does not persist`() = runTest {
        val persistence = InMemoryDeviceNamePersistence("original")
        val store = DeviceNameStore(persistence)
        store.init()
        val writesBefore = persistence.writes
        store.setName("  ")
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `setName empty string fails`() = runTest {
        val persistence = InMemoryDeviceNamePersistence(null)
        val store = DeviceNameStore(persistence)
        store.init()
        val writesBefore = persistence.writes
        val result = store.setName("")
        assertTrue(result.isFailure)
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `init falls back to default when persistence read throws`() = runTest {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(readError = RuntimeException("storage error")))
        store.init()
        val name = store.name.first()
        assertTrue(name.isNotEmpty(), "Expected non-empty default after read error, got: '$name'")
    }

    @Test
    fun `setName returns failure and does not mutate state when persistence write throws`() = runTest {
        val persistence = InMemoryDeviceNamePersistence(
            stored = "original",
            writeError = RuntimeException("disk full"),
        )
        val store = DeviceNameStore(persistence)
        store.init()
        val before = store.name.first()
        val result = store.setName("NewName")
        assertTrue(result.isFailure)
        assertEquals(before, store.name.first())
        assertEquals("original", persistence.read())
    }
}
