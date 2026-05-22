@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.InMemoryDeviceNamePersistence
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepublishOnNameChangeTest {
    @Test
    fun `initial value at launch is not republished`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence("initial"))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val job: Job = republishOnNameChange(store, discovery)
        advanceUntilIdle()
        assertTrue(discovery.republishCalls.isEmpty())
        job.cancel()
    }

    @Test
    fun `one distinct subsequent value triggers one republish`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val job: Job = republishOnNameChange(store, discovery)
        store.setName("NewName")
        advanceUntilIdle()
        assertEquals(listOf("NewName"), discovery.republishCalls)
        job.cancel()
    }

    // MutableStateFlow deduplicates equal values; `distinctUntilChanged` is not needed in the chain.
    @Test
    fun `duplicate consecutive value does not trigger second republish`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val job: Job = republishOnNameChange(store, discovery)
        store.setName("Name")
        store.setName("Name")
        advanceUntilIdle()
        assertEquals(1, discovery.republishCalls.size)
        job.cancel()
    }

    @Test
    fun `cancelling the job stops further publishes`() = runTest(UnconfinedTestDispatcher()) {
        val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
        store.init()
        val discovery = FakeDeviceDiscovery()
        val job: Job = republishOnNameChange(store, discovery)
        job.cancel()
        store.setName("AfterCancel")
        advanceUntilIdle()
        assertTrue(discovery.republishCalls.isEmpty())
    }

    @Test
    fun `two independent jobs each forward and cancelling one does not cancel the other`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DeviceNameStore(InMemoryDeviceNamePersistence(null))
            store.init()
            val d1 = FakeDeviceDiscovery()
            val d2 = FakeDeviceDiscovery()
            val job1: Job = republishOnNameChange(store, d1)
            val job2: Job = republishOnNameChange(store, d2)

            store.setName("First")
            advanceUntilIdle()
            assertEquals(listOf("First"), d1.republishCalls)
            assertEquals(listOf("First"), d2.republishCalls)

            job1.cancel()

            store.setName("Second")
            advanceUntilIdle()
            assertEquals(listOf("First"), d1.republishCalls)
            assertEquals(listOf("First", "Second"), d2.republishCalls)

            job2.cancel()
        }
}
