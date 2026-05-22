package com.tubetoast.tether.config

import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferencesPersistenceTest {
    private lateinit var prefs: Preferences
    private lateinit var persistence: DeviceNamePersistenceJvm

    @BeforeTest
    fun setUp() {
        prefs = Preferences.userRoot().node("tether-test-${UUID.randomUUID()}")
        persistence = DeviceNamePersistenceJvm(prefs)
    }

    @AfterTest
    fun tearDown() {
        runCatching { prefs.removeNode() }
    }

    @Test
    fun `read returns null when no value stored`() = runTest {
        assertNull(persistence.read())
    }

    @Test
    fun `write then read round-trips value`() = runTest {
        persistence.write("MyDesktop")
        assertEquals("MyDesktop", persistence.read())
    }

    @Test
    fun `write overwrites previous value`() = runTest {
        persistence.write("First")
        persistence.write("Second")
        assertEquals("Second", persistence.read())
    }
}
