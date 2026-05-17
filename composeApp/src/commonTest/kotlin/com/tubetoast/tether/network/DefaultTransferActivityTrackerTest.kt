package com.tubetoast.tether.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTransferActivityTrackerTest {
    @Test
    fun `first enter fires onFirstEnter`() = runTest {
        var enters = 0
        val tracker = DefaultTransferActivityTracker(onFirstEnter = { enters++ })
        tracker.withActiveTransfer {}
        assertEquals(1, enters)
    }

    @Test
    fun `second enter does not re-fire onFirstEnter`() = runTest {
        var enters = 0
        val tracker = DefaultTransferActivityTracker(onFirstEnter = { enters++ })
        tracker.withActiveTransfer {
            tracker.withActiveTransfer {}
        }
        assertEquals(1, enters)
    }

    @Test
    fun `intermediate exit does not fire onLastExit`() = runTest {
        var exits = 0
        val tracker = DefaultTransferActivityTracker(onLastExit = { exits++ })
        tracker.withActiveTransfer {
            tracker.withActiveTransfer {}
            assertEquals(0, exits)
        }
    }

    @Test
    fun `last exit fires onLastExit exactly once`() = runTest {
        var exits = 0
        val tracker = DefaultTransferActivityTracker(onLastExit = { exits++ })
        tracker.withActiveTransfer {
            tracker.withActiveTransfer {}
        }
        assertEquals(1, exits)
    }

    @Test
    fun `exception inside withActiveTransfer still decrements and fires onLastExit`() = runTest {
        var exits = 0
        val tracker = DefaultTransferActivityTracker(onLastExit = { exits++ })
        runCatching { tracker.withActiveTransfer { error("boom") } }
        assertEquals(1, exits)
    }

    @Test
    fun `two parallel withActiveTransfer cause only one acquire and one release`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        var enters = 0
        var exits = 0
        val tracker = DefaultTransferActivityTracker(
            onFirstEnter = { enters++ },
            onLastExit = { exits++ },
        )
        val barrier = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job1 = async {
            tracker.withActiveTransfer {
                barrier.await()
            }
        }
        val job2 = async {
            tracker.withActiveTransfer {
                barrier.await()
            }
        }
        barrier.complete(Unit)
        awaitAll(job1, job2)
        assertEquals(1, enters)
        assertEquals(1, exits)
    }

    @Test
    fun `releaseAll when count greater than zero fires onLastExit and resets`() = runTest(UnconfinedTestDispatcher()) {
        var exits = 0
        val tracker = DefaultTransferActivityTracker(onLastExit = { exits++ })
        val barrier = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = async { tracker.withActiveTransfer { barrier.await() } }
        tracker.releaseAll()
        barrier.complete(Unit)
        job.await()
        assertEquals(1, exits)
    }

    @Test
    fun `releaseAll when count is zero fires nothing`() = runTest {
        var exits = 0
        val tracker = DefaultTransferActivityTracker(onLastExit = { exits++ })
        tracker.releaseAll()
        assertEquals(0, exits)
    }
}
