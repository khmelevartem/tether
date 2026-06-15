package com.tubetoast.tether.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfirmableFileSourceTest {
    @Test
    fun `confirmDelivered fires callback exactly once`() {
        var count = 0
        val source = ConfirmableFileSource(FakeFileSource("f.txt", 10L)) { count++ }

        source.confirmDelivered()
        assertEquals(1, count)

        source.confirmDelivered()
        assertEquals(1, count, "second confirmDelivered must be a no-op")
    }

    @Test
    fun `close does not fire the delivery callback`() {
        var fired = false
        val source = ConfirmableFileSource(FakeFileSource("f.txt", 10L)) { fired = true }

        source.close()
        assertFalse(fired, "close() must not trigger the delivery callback")
    }

    @Test
    fun `delegates name and sizeBytes to inner`() {
        val inner = FakeFileSource("data.bin", 42L)
        val source = ConfirmableFileSource(inner) {}

        assertEquals("data.bin", source.name)
        assertEquals(42L, source.sizeBytes)
    }

    @Test
    fun `delegates close to inner`() {
        val inner = FakeFileSource("f.txt", 1L)
        val source = ConfirmableFileSource(inner) {}

        source.close()
        assertTrue(inner.closeCalled)
    }
}
