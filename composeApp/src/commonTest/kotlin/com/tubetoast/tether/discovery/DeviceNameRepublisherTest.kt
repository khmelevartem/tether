@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.InMemoryDeviceNamePersistence
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceNameRepublisherTest {
    @Test
    fun `initial emit is not republished`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence("initial"))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        assertTrue(discovery.republishCalls.isEmpty())
        republisher.stop()
    }

    @Test
    fun `one distinct name change triggers one republish`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        store.setName("NewName")
        advanceUntilIdle()
        assertEquals(listOf("NewName"), discovery.republishCalls)
        republisher.stop()
    }

    @Test
    fun `duplicate consecutive value does not trigger second republish`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        store.setName("Name")
        store.setName("Name")
        advanceUntilIdle()
        assertEquals(1, discovery.republishCalls.size)
        republisher.stop()
    }

    @Test
    fun `start called twice republishes only once per change`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        republisher.start(this)
        store.setName("Once")
        advanceUntilIdle()
        assertEquals(1, discovery.republishCalls.size)
        republisher.stop()
    }

    @Test
    fun `stop is idempotent`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        republisher.stop()
        republisher.stop()
        store.setName("AfterStop")
        advanceUntilIdle()
        assertTrue(discovery.republishCalls.isEmpty())
    }
}
