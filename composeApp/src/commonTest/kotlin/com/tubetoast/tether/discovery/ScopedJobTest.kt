package com.tubetoast.tether.discovery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ScopedJobTest {
    @Test
    fun `start launches the block`() = runTest(UnconfinedTestDispatcher()) {
        val job = ScopedJob()
        var runs = 0
        job.start(this) { runs++ }
        advanceUntilIdle()
        assertEquals(1, runs)
    }

    @Test
    fun `double start while active is a no-op`() {
        val scope = TestScope(UnconfinedTestDispatcher())
        val job = ScopedJob()
        var runs = 0
        // Block suspends so the job remains active when the second start is attempted.
        job.start(scope) {
            runs++
            kotlinx.coroutines.awaitCancellation()
        }
        job.start(scope) { runs++ }
        job.stop()
        scope.advanceUntilIdle()
        assertEquals(1, runs)
    }

    @Test
    fun `stop cancels the running job`() {
        val scope = TestScope(UnconfinedTestDispatcher())
        val job = ScopedJob()
        var runs = 0
        // Block suspends indefinitely; stop should cancel it before it completes.
        job.start(scope) {
            kotlinx.coroutines.awaitCancellation()
            runs++
        }
        job.stop()
        scope.advanceUntilIdle()
        assertEquals(0, runs)
    }

    @Test
    fun `start after stop re-launches the block`() {
        val scope = TestScope(UnconfinedTestDispatcher())
        val job = ScopedJob()
        var runs = 0
        job.start(scope) { runs++ }
        scope.advanceUntilIdle()
        job.stop()
        job.start(scope) { runs++ }
        scope.advanceUntilIdle()
        assertEquals(2, runs)
    }
}
