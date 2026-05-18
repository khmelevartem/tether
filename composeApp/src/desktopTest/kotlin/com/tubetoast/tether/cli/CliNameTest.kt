package com.tubetoast.tether.cli

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.InMemoryDeviceNamePersistence
import com.tubetoast.tether.handleName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliNameTest {
    private fun storeWith(initial: String? = null): DeviceNameStore =
        DeviceNameStore(InMemoryDeviceNamePersistence(initial))

    @Test
    fun `success prints OK and persists`() = runTest {
        val persistence = InMemoryDeviceNamePersistence(null)
        val store = DeviceNameStore(persistence)
        store.init()

        val output = mutableListOf<String>()
        handleName(store, "Alice", output::add)

        assertEquals(1, output.size)
        assertTrue(output[0].startsWith("OK name=Alice"), "expected OK, got: ${output[0]}")
        assertEquals("Alice", persistence.read())
    }

    @Test
    fun `empty arg prints ERR and does not persist`() = runTest {
        val persistence = InMemoryDeviceNamePersistence(null)
        val store = DeviceNameStore(persistence)
        store.init()
        val writesBefore = persistence.writes

        val output = mutableListOf<String>()
        handleName(store, "", output::add)

        assertTrue(output[0].startsWith("ERR"), "expected ERR, got: ${output[0]}")
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `arg over 50 codepoints prints ERR and does not persist`() = runTest {
        val persistence = InMemoryDeviceNamePersistence(null)
        val store = DeviceNameStore(persistence)
        store.init()
        val writesBefore = persistence.writes

        val longName = "A".repeat(51)
        val output = mutableListOf<String>()
        handleName(store, longName, output::add)

        assertTrue(output[0].startsWith("ERR"), "expected ERR, got: ${output[0]}")
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `whitespace-only arg prints ERR and does not persist`() = runTest {
        val persistence = InMemoryDeviceNamePersistence(null)
        val store = DeviceNameStore(persistence)
        store.init()
        val writesBefore = persistence.writes

        val output = mutableListOf<String>()
        handleName(store, "   ", output::add)

        assertTrue(output[0].startsWith("ERR"), "expected ERR, got: ${output[0]}")
        assertEquals(writesBefore, persistence.writes)
    }
}
