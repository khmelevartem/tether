package com.tubetoast.tether.config

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceNamePersistenceAppleTest {
    private val suiteName = "tether-test-persistence"
    private val defaults = NSUserDefaults(suiteName = suiteName)
    private val persistence = DeviceNamePersistenceApple(defaults)

    @BeforeTest
    fun setUp() {
        defaults.removeObjectForKey("tether_device_name")
        defaults.synchronize()
    }

    @AfterTest
    fun tearDown() {
        defaults.removeObjectForKey("tether_device_name")
        defaults.synchronize()
    }

    @Test
    fun `read returns null on empty store`() = runTest {
        assertNull(persistence.read())
    }

    @Test
    fun `write then read round-trips value`() = runTest {
        persistence.write("MyMac")
        assertEquals("MyMac", persistence.read())
    }

    @Test
    fun `write overwrites previous value`() = runTest {
        persistence.write("First")
        persistence.write("Second")
        assertEquals("Second", persistence.read())
    }
}
