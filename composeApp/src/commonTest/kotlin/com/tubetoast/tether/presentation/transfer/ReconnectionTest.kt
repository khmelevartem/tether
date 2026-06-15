package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.BatchOutcome
import com.tubetoast.tether.transfer.BatchProgress
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.FakeConnectionMonitor
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.transfer.TransferErrorReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectionTest {
    private fun sources(vararg names: String) = names.map { FakeFileSource(it, 100L) }

    @Test
    fun `each drop restarts its own reconnect window independently`() = runTest {
        val monitor = FakeConnectionMonitor()
        var sendCount = 0
        val pauseOnSecond = Channel<Unit>(0)
        val pauseOnFourth = Channel<Unit>(0)

        val sender = BatchSender(
            sendOne = { src, onProgress ->
                sendCount++
                when (sendCount) {
                    2 -> pauseOnSecond.receive()
                    4 -> pauseOnFourth.receive()
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        val emitted = mutableListOf<BatchProgress>()
        val job = launch {
            sender.run(sources("a.txt", "b.txt", "c.txt")) { emitted.add(it) }
        }

        runCurrent()
        monitor.drop()
        runCurrent()
        monitor.reconnect(true)
        runCurrent()

        monitor.drop()
        runCurrent()
        monitor.reconnect(true)
        runCurrent()

        job.join()

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        assertIs<BatchOutcome.AllSent>(last.outcome)
        assertEquals(3, last.perFile.count { it is PerFileStatus.Done })
        assertNull(last.perFile.filterIsInstance<PerFileStatus.Failed>().firstOrNull())
    }

    @Test
    fun `successful first reconnect does not prevent second drop from reconnecting`() = runTest {
        val monitor = FakeConnectionMonitor()
        var sendCount = 0
        val pause = Channel<Unit>(0)

        val sender = BatchSender(
            sendOne = { src, onProgress ->
                sendCount++
                if (sendCount == 2 || sendCount == 3) pause.receive()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        var outcome: BatchOutcome? = null
        val job = launch {
            outcome = sender.run(sources("a.txt", "b.txt", "c.txt")) {}
        }

        runCurrent()
        monitor.drop()
        runCurrent()
        monitor.reconnect(true)
        runCurrent()

        monitor.drop()
        runCurrent()
        monitor.reconnect(true)
        runCurrent()

        job.join()

        assertIs<BatchOutcome.AllSent>(outcome)
    }

    @Test
    fun `second drop with timeout produces Failed even after first successful reconnect`() = runTest {
        val monitor = FakeConnectionMonitor()
        var sendCount = 0
        val pause = Channel<Unit>(0)

        val sender = BatchSender(
            sendOne = { src, onProgress ->
                sendCount++
                if (sendCount == 2 || sendCount == 3) pause.receive()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        var outcome: BatchOutcome? = null
        val job = launch {
            outcome = sender.run(sources("a.txt", "b.txt", "c.txt")) {}
        }

        runCurrent()
        monitor.drop()
        runCurrent()
        monitor.reconnect(true)
        runCurrent()

        monitor.drop()
        runCurrent()
        monitor.reconnect(false)
        runCurrent()

        job.join()

        val failedOutcome = outcome
        assertIs<BatchOutcome.Failed>(failedOutcome)
        assertEquals(TransferErrorReason.NetworkLost, failedOutcome.reason)
    }
}
