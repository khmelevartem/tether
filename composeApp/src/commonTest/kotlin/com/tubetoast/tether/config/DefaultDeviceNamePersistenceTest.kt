package com.tubetoast.tether.config

import com.tubetoast.tether.preferences.TempDataStore
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultDeviceNamePersistenceTest {
    private lateinit var tempDataStore: TempDataStore
    private lateinit var persistence: DefaultDeviceNamePersistence

    @BeforeTest
    fun setUp() {
        tempDataStore = TempDataStore()
        persistence = DefaultDeviceNamePersistence(tempDataStore.dataStore)
    }

    @AfterTest
    fun tearDown() {
        tempDataStore.tearDown()
    }

    @Test
    fun `read returns null when nothing stored`() = runTest {
        assertNull(persistence.read())
    }

    @Test
    fun `write then read round-trips value`() = runTest {
        persistence.write("MyDevice")
        assertEquals("MyDevice", persistence.read())
    }

    @Test
    fun `write overwrites previous value`() = runTest {
        persistence.write("A")
        persistence.write("B")
        assertEquals("B", persistence.read())
    }
}
