package com.tubetoast.tether.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LostDebouncerTest {
    @Test
    fun `scheduleRemoval triggers onRemove after debounce window`() = runTest {
        val removed = mutableListOf<String>()
        val debouncer = LostDebouncer(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            debounceMs = 10_000L,
            onRemove = removed::add,
        )

        debouncer.scheduleRemoval("peer-a")
        advanceTimeBy(10_001L)

        assertEquals(listOf("peer-a"), removed)
    }

    @Test
    fun `cancelRemoval before window prevents onRemove`() = runTest {
        val removed = mutableListOf<String>()
        val debouncer = LostDebouncer(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            debounceMs = 10_000L,
            onRemove = removed::add,
        )

        debouncer.scheduleRemoval("peer-a")
        advanceTimeBy(5_000L)
        debouncer.cancelRemoval("peer-a")
        advanceTimeBy(10_000L)

        assertTrue(removed.isEmpty())
    }

    @Test
    fun `lost then found within window leaves peer present`() = runTest {
        val removed = mutableListOf<String>()
        val debouncer = LostDebouncer(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            debounceMs = 10_000L,
            onRemove = removed::add,
        )

        debouncer.scheduleRemoval("peer-a")
        advanceTimeBy(7_000L)
        debouncer.cancelRemoval("peer-a")
        advanceTimeBy(10_000L)

        assertTrue(removed.isEmpty(), "peer should not be removed when re-found before debounce window")
    }

    @Test
    fun `cancelAll cancels all pending removals`() = runTest {
        val removed = mutableListOf<String>()
        val debouncer = LostDebouncer(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            debounceMs = 10_000L,
            onRemove = removed::add,
        )

        debouncer.scheduleRemoval("peer-a")
        debouncer.scheduleRemoval("peer-b")
        debouncer.cancelAll()
        advanceTimeBy(15_000L)

        assertTrue(removed.isEmpty())
    }

    @Test
    fun `second scheduleRemoval for same name resets the window`() = runTest {
        val removed = mutableListOf<String>()
        val debouncer = LostDebouncer(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            debounceMs = 10_000L,
            onRemove = removed::add,
        )

        debouncer.scheduleRemoval("peer-a")
        advanceTimeBy(8_000L)
        debouncer.scheduleRemoval("peer-a")
        advanceTimeBy(8_000L)
        assertTrue(removed.isEmpty(), "removal should not have fired yet — window was reset")

        advanceTimeBy(3_000L)
        assertEquals(listOf("peer-a"), removed)
    }
}
