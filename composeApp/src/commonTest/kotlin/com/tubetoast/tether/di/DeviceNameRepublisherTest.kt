@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.InMemoryDeviceNamePersistence
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceNameRepublisherTest {
    @Test
    fun `start subscribes to subsequent name changes`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        store.setName("X")
        advanceUntilIdle()
        assertEquals(listOf("X"), discovery.republishCalls)
        republisher.stop()
    }

    @Test
    fun `initial value is not republished`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        advanceUntilIdle()
        assertTrue(discovery.republishCalls.isEmpty())
        republisher.stop()
    }

    @Test
    fun `stop stops further republishes`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        store.setName("X")
        advanceUntilIdle()
        republisher.stop()
        store.setName("Y")
        advanceUntilIdle()
        assertEquals(listOf("X"), discovery.republishCalls)
    }

    @Test
    fun `start is idempotent`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val republisher = DeviceNameRepublisher(store, discovery)
        republisher.start(this)
        republisher.start(this)
        store.setName("Z")
        advanceUntilIdle()
        assertEquals(listOf("Z"), discovery.republishCalls)
        republisher.stop()
    }
}
