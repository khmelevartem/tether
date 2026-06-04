package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPickerCoordinatorTest {
    private val coordinator = AndroidPickerCoordinator()

    private fun fakeSource(name: String): FileSource = object : FileSource {
        override val relativePath: String = name
        override val name: String = name
        override val sizeBytes: Long = 0L

        override suspend fun openReadChannel(): ByteReadChannel = ByteReadChannel.Empty

        override fun close() = Unit
    }

    @Test
    fun `begin then resolve completes the deferred with the given sources`() = runTest {
        val sources = listOf(fakeSource("a.txt"))
        val deferred = coordinator.begin()
        coordinator.resolve(sources)
        assertEquals(sources, deferred.await())
    }

    @Test
    fun `a second begin cancels the first deferred`() = runTest {
        val first = coordinator.begin()
        coordinator.begin()
        assertTrue(first.isCancelled, "first deferred must be cancelled when a second begin is called")
    }

    @Test
    fun `resolve when idle is a no-op and does not throw`() {
        coordinator.resolve(emptyList())
    }

    @Test
    fun `launchFiles returns null when no launcher is attached`() {
        val result = coordinator.launchFiles()
        assertEquals(null, result, "launchFiles must return null when no file launcher is attached")
    }

    @Test
    fun `launchFolder returns null when no launcher is attached`() {
        val result = coordinator.launchFolder()
        assertEquals(null, result, "launchFolder must return null when no folder launcher is attached")
    }

    @Test
    fun `launchPhotos returns null when no launcher is attached`() {
        val result = coordinator.launchPhotos()
        assertEquals(null, result, "launchPhotos must return null when no photos launcher is attached")
    }

    @Test
    fun `deferred completed by resolve is not cancelled by a subsequent begin`() = runTest {
        val first = coordinator.begin()
        coordinator.resolve(listOf(fakeSource("b.txt")))
        assertTrue(first.isCompleted, "first deferred must be completed before begin is called again")
        assertFalse(first.isCancelled, "completed deferred must not be treated as in-flight")

        coordinator.begin()
        assertTrue(first.isCompleted)
    }
}
