package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class BatchSenderTest {
    private val peer = PeerIdentity("peer-test")

    private fun sources(vararg names: String): List<FakeFileSource> =
        names.map { FakeFileSource(it, 100L) }

    private fun instantSend(): suspend (FileSource, (Long, Long?) -> Unit) -> Unit =
        { src, onProgress -> onProgress(src.sizeBytes ?: 0L, src.sizeBytes) }

    private fun makeSender(
        monitor: FakeConnectionMonitor = FakeConnectionMonitor(),
        sendOne: suspend (FileSource, (Long, Long?) -> Unit) -> Unit = instantSend(),
    ) = BatchSender(
        sendOne = sendOne,
        connectionMonitor = monitor,
        progressThrottle = 100.milliseconds,
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `happy path all succeed returns AllSent and emits Completed AllSent`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val outcome = makeSender().run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.AllSent>(outcome)
        val completed = emitted.last()
        assertIs<BatchProgress.Completed>(completed)
        assertIs<BatchOutcome.AllSent>(completed.outcome)
    }

    @Test
    fun `happy path emits Sending before Completed`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        makeSender().run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        val sending = emitted.dropLast(1)
        assertTrue(sending.isNotEmpty(), "Expected at least one Sending before Completed")
        sending.forEach { assertIs<BatchProgress.Sending>(it) }
    }

    @Test
    fun `cancel mid-file returns Cancelled with remaining files`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val monitor = FakeConnectionMonitor()
        val sender = BatchSender(
            sendOne = { _, _ -> pauseChannel.receive() },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        val job = launch {
            sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }
        }

        runCurrent()
        job.cancel()
        runCurrent()

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val cancelled = last.outcome
        assertIs<BatchOutcome.Cancelled>(cancelled)
        assertTrue(cancelled.remaining.isNotEmpty(), "Expected remaining files")
    }

    @Test
    fun `unreadable file skips and rest succeed with FilesUnreadable partial reason`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val sender = makeSender(
            sendOne = { src, onProgress ->
                if (src.name == "b.txt") throw UnreadableSourceException("b.txt")
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        assertIs<BatchOutcome.PartialSent>(last.outcome)

        val bFile = last.perFile.first { it.name == "b.txt" }
        assertIs<PerFileStatus.Failed>(bFile)
        assertIs<FailureReason.Unreadable>(bFile.reason)
    }

    @Test
    fun `ReceiverWriteFailed file is marked failed and batch continues`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val sender = makeSender(
            sendOne = { src, onProgress ->
                if (src.name == "b.txt") throw ReceiverWriteFailedException(507)
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val bFile = last.perFile.first { it.name == "b.txt" }
        assertIs<PerFileStatus.Failed>(bFile)
        val reason = bFile.reason
        assertIs<FailureReason.ReceiverWriteFailed>(reason)
        assertEquals(507, reason.httpStatus)
    }

    @Test
    fun `connection drop then reconnect resumes and produces AllSent`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val monitor = FakeConnectionMonitor()
        var sendCallCount = 0
        val sender = BatchSender(
            sendOne = { src, onProgress ->
                sendCallCount++
                if (sendCallCount == 2) pauseChannel.receive()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        val job = launch {
            sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }
        }

        runCurrent()
        monitor.drop()
        runCurrent()
        monitor.reconnect(true)
        runCurrent()
        advanceUntilIdle()

        job.join()

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        assertIs<BatchOutcome.AllSent>(last.outcome)
        assertEquals(3, last.perFile.count { it is PerFileStatus.Done })
        assertNull(last.perFile.filterIsInstance<PerFileStatus.Failed>().firstOrNull())
    }

    @Test
    fun `connection drop then reconnect false produces Failed NetworkLost`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val monitor = FakeConnectionMonitor()
        var sendCallCount = 0
        val sender = BatchSender(
            sendOne = { src, onProgress ->
                sendCallCount++
                if (sendCallCount == 2) pauseChannel.receive()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        var outcome: BatchOutcome? = null
        val job = launch {
            outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }
        }

        runCurrent()
        monitor.drop()
        runCurrent()
        monitor.reconnect(false)
        runCurrent()

        job.join()

        val failedOutcome = outcome
        assertIs<BatchOutcome.Failed>(failedOutcome)
        assertEquals(TransferErrorReason.NetworkLost, failedOutcome.reason)
        assertEquals(1, failedOutcome.sent)

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val completedOutcome = last.outcome
        assertIs<BatchOutcome.Failed>(completedOutcome)
        assertEquals(TransferErrorReason.NetworkLost, completedOutcome.reason)
        assertEquals(1, completedOutcome.sent)

        val failedFiles = last.perFile.filterIsInstance<PerFileStatus.Failed>()
        assertEquals(2, failedFiles.size)
        failedFiles.forEach { assertEquals(FailureReason.NetworkLost, it.reason) }
    }

    @Test
    fun `empty source list returns AllSent without emitting`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val outcome = makeSender().run(emptyList(), peer) { emitted.add(it) }

        assertIs<BatchOutcome.AllSent>(outcome)
        assertTrue(emitted.isEmpty(), "Expected no emissions for empty source list")
    }

    @Test
    fun `running with subset only calls sendOne for those files`() = runTest {
        val called = mutableListOf<String>()
        val sender = BatchSender(
            sendOne = { src, onProgress ->
                called.add(src.name)
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
            connectionMonitor = FakeConnectionMonitor(),
            progressThrottle = 100.milliseconds,
        )

        sender.run(sources("b.txt"), peer) {}

        assertEquals(listOf("b.txt"), called)
    }

    @Test
    fun `PeerUnreachable exception produces FailureReason PeerUnreachable per file`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val sender = makeSender(
            sendOne = { src, onProgress ->
                if (src.name == "b.txt") throw PeerUnreachableException()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val bFile = last.perFile.first { it.name == "b.txt" }
        assertIs<PerFileStatus.Failed>(bFile)
        assertEquals(FailureReason.PeerUnreachable, bFile.reason)
    }

    @Test
    fun `all files PeerUnreachable produces Failed PeerUnreachable`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val sender = makeSender(
            sendOne = { _, _ -> throw PeerUnreachableException() },
        )

        val outcome = sender.run(sources("a.txt", "b.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.Failed>(outcome)
        assertEquals(TransferErrorReason.PeerUnreachable, outcome.reason)
        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val lastOutcome = last.outcome
        assertIs<BatchOutcome.Failed>(lastOutcome)
        assertEquals(TransferErrorReason.PeerUnreachable, lastOutcome.reason)
    }

    @Test
    fun `all files ReceiverWriteFailed produces Failed ReceiverWriteFailed`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val sender = makeSender(
            sendOne = { _, _ -> throw ReceiverWriteFailedException(507) },
        )

        val outcome = sender.run(sources("a.txt", "b.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.Failed>(outcome)
        assertEquals(TransferErrorReason.ReceiverWriteFailed, outcome.reason)
        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val lastOutcome = last.outcome
        assertIs<BatchOutcome.Failed>(lastOutcome)
        assertEquals(TransferErrorReason.ReceiverWriteFailed, lastOutcome.reason)
    }

    @Test
    fun `mixed failure types with no successes produces Failed AllFilesFailed`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        var callCount = 0
        val sender = makeSender(
            sendOne = { _, _ ->
                callCount++
                if (callCount == 1) throw PeerUnreachableException() else throw ReceiverWriteFailedException(500)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.Failed>(outcome)
        assertEquals(TransferErrorReason.AllFilesFailed, outcome.reason)
        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val lastOutcome = last.outcome
        assertIs<BatchOutcome.Failed>(lastOutcome)
        assertEquals(TransferErrorReason.AllFilesFailed, lastOutcome.reason)
    }

    @Test
    fun `cancel marks remaining files with TransferCancelled reason`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val monitor = FakeConnectionMonitor()
        val sender = BatchSender(
            sendOne = { _, _ -> pauseChannel.receive() },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        val job = launch {
            sender.run(sources("a.txt", "b.txt"), peer) { emitted.add(it) }
        }

        runCurrent()
        job.cancel()
        runCurrent()

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        assertIs<BatchOutcome.Cancelled>(last.outcome)
        val cancelledFiles = last.perFile.filterIsInstance<PerFileStatus.Failed>()
        assertTrue(cancelledFiles.any { it.reason == FailureReason.TransferCancelled })
    }

    @Test
    fun `all files skipped by user produces PartialSent`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        val sender = makeSender()

        val outcome = sender.run(sources("a.txt", "b.txt"), peer, skipPredicate = { true }) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        assertIs<BatchOutcome.PartialSent>(last.outcome)
    }

    @Test
    fun `cancel before first file produces Cancelled with sent=0 and all files remaining`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val emitted = mutableListOf<BatchProgress>()
        val monitor = FakeConnectionMonitor()
        val sender = BatchSender(
            sendOne = { _, _ -> gate.await() },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        val srcs = sources("a.txt", "b.txt", "c.txt")
        val job = launch {
            sender.run(srcs, peer) { emitted.add(it) }
        }

        runCurrent()
        job.cancel()
        runCurrent()

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val cancelled = last.outcome
        assertIs<BatchOutcome.Cancelled>(cancelled)
        assertEquals(0, cancelled.sent)
        assertEquals(srcs.size, cancelled.remaining.size)
        assertTrue(cancelled.remaining.containsAll(srcs.map { it.name }))
    }

    @Test
    fun `cancelCurrent arriving after sendOne completes does not mislabel file as CancelledByUser`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val emitted = mutableListOf<BatchProgress>()
        var sendOneCompleted = false
        val sender = BatchSender(
            sendOne = { src, onProgress ->
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                sendOneCompleted = true
            },
            connectionMonitor = FakeConnectionMonitor(),
            progressThrottle = 100.milliseconds,
            dispatcher = testDispatcher,
        )

        val job = launch(testDispatcher) {
            sender.run(sources("a.txt"), peer) { emitted.add(it) }
        }
        advanceUntilIdle()

        val cancelResult = sender.cancelCurrent("a.txt")
        job.join()

        assertTrue(sendOneCompleted, "sendOne must complete bytes before cancelCurrent arrives")
        val completed = emitted.filterIsInstance<BatchProgress.Completed>().lastOrNull()
        assertTrue(
            completed != null &&
                completed.perFile.none {
                    it is PerFileStatus.Failed &&
                        (it.reason is FailureReason.CancelledByUser) &&
                        it.name == "a.txt"
                },
            "sendOne completed fully but a.txt is still marked CancelledByUser (cancelResult=$cancelResult)",
        )
    }

    @Test
    fun `remaining list excludes skipped files on cancel`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val monitor = FakeConnectionMonitor()
        val sender = BatchSender(
            sendOne = { src, onProgress ->
                if (src.name != "file1.txt") pauseChannel.receive()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
            connectionMonitor = monitor,
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
        )

        val skipped = mutableSetOf("file2.txt")
        val job = launch {
            sender.run(
                sources("file1.txt", "file2.txt", "file3.txt"),
                peer,
                skipPredicate = { it.name in skipped },
            ) { emitted.add(it) }
        }

        runCurrent()
        job.cancel()
        runCurrent()

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val lastOutcome = last.outcome
        assertIs<BatchOutcome.Cancelled>(lastOutcome)
        assertTrue("file2.txt" !in lastOutcome.remaining, "Skipped file must not appear in remaining")
    }

    @Test
    fun `Sending totalBytes sums sizes of all sources`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        makeSender().run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        val sending = emitted.filterIsInstance<BatchProgress.Sending>().first()
        assertEquals(300L, sending.totalBytes) // 3 sources * 100 bytes
    }

    @Test
    fun `Sending totalBytes is null before materialization and resolves once the size is known`() = runTest {
        val gate = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val sender = BatchSender(
            sendOne = { src, onProgress ->
                (src as LateSizeSource).materialize()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                gate.receive() // hold the file in-flight so a throttled progress emit can occur
            },
            connectionMonitor = FakeConnectionMonitor(),
            progressThrottle = 100.milliseconds,
            // Test scheduler (not Unconfined) so the progress throttle delay is virtual-time driven.
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val job = launch { sender.run(listOf(LateSizeSource("a", 150L)), peer) { emitted.add(it) } }

        runCurrent()
        // First emit precedes the source being opened → size still unknown.
        assertNull(emitted.filterIsInstance<BatchProgress.Sending>().first().totalBytes)

        advanceTimeBy(150.milliseconds)
        runCurrent()
        // A throttled progress emit after materialization carries the now-known batch total.
        assertEquals(150L, emitted.filterIsInstance<BatchProgress.Sending>().last().totalBytes)

        gate.send(Unit)
        advanceUntilIdle()
        job.join()
    }

    @Test
    fun `lazy source reports preparing until the first byte then sending`() = runTest {
        val materializeGate = Channel<Unit>(0)
        val streamGate = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val sender = BatchSender(
            sendOne = { _, onProgress ->
                materializeGate.receive() // the openReadChannel export gap
                onProgress(50L, 100L) // first byte → streaming begins
                streamGate.receive() // stay in-flight so a throttled progress emit lands
                onProgress(100L, 100L)
            },
            connectionMonitor = FakeConnectionMonitor(),
            progressThrottle = 100.milliseconds,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val job = launch {
            sender.run(
                listOf(FakeFileSource("photo.jpg", 100L, materializesLazily = true)),
                peer,
            ) { emitted.add(it) }
        }

        runCurrent()
        val beforeByte = emitted.filterIsInstance<BatchProgress.Sending>()
        assertTrue(beforeByte.isNotEmpty() && beforeByte.all { it.preparing }, "preparing until first byte")

        materializeGate.send(Unit)
        advanceTimeBy(150.milliseconds)
        runCurrent()
        assertFalse(emitted.filterIsInstance<BatchProgress.Sending>().last().preparing, "sending after first byte")

        streamGate.send(Unit)
        advanceUntilIdle()
        job.join()
    }

    @Test
    fun `transfer activity is held once across the batch despite per-file sends`() = runTest {
        var enters = 0
        var exits = 0
        val tracker = DefaultTransferActivityTracker(
            scope = backgroundScope,
            onFirstEnter = { enters++ },
            onLastExit = { exits++ },
        )
        val sender = BatchSender(
            // Each file nests its own hold (as FileClient.send does), so the batch hold must bridge
            // the gaps — otherwise the count hits 0 between files and the foreground banner flickers.
            sendOne = { _, onProgress -> tracker.withActiveTransfer { onProgress(100L, 100L) } },
            connectionMonitor = FakeConnectionMonitor(),
            progressThrottle = 100.milliseconds,
            dispatcher = Dispatchers.Unconfined,
            tracker = tracker,
        )

        sender.run(sources("a.txt", "b.txt", "c.txt"), peer) {}

        assertEquals(1, enters, "activity acquired once at batch start")
        assertEquals(1, exits, "activity released once at batch end — no per-file flicker")
    }

    @Test
    fun `eagerly-readable sources never report preparing`() = runTest {
        val emitted = mutableListOf<BatchProgress>()
        makeSender().run(sources("a.txt", "b.txt"), peer) { emitted.add(it) }

        assertTrue(emitted.filterIsInstance<BatchProgress.Sending>().none { it.preparing })
    }

    @Test
    fun `cancelling a lazy source during its preparing gap marks it cancelled`() = runTest {
        val materializeGate = Channel<Unit>(0)
        val emitted = mutableListOf<BatchProgress>()
        val sender = BatchSender(
            sendOne = { _, onProgress ->
                materializeGate.receive() // never released — cancel arrives during the export gap
                onProgress(100L, 100L)
            },
            connectionMonitor = FakeConnectionMonitor(),
            progressThrottle = 100.milliseconds,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val job = launch {
            sender.run(
                listOf(FakeFileSource("photo.jpg", 100L, materializesLazily = true)),
                peer,
            ) { emitted.add(it) }
        }

        runCurrent()
        val sendings = emitted.filterIsInstance<BatchProgress.Sending>()
        assertTrue(sendings.isNotEmpty() && sendings.all { it.preparing }, "should be preparing before cancel")

        job.cancel()
        runCurrent()

        val last = emitted.last()
        assertIs<BatchProgress.Completed>(last)
        val cancelled = last.outcome
        assertIs<BatchOutcome.Cancelled>(cancelled)
        assertTrue("photo.jpg" in cancelled.remaining, "a lazy source cancelled mid-preparing is left unsent")
    }

    private class LateSizeSource(
        override val name: String,
        private val realSize: Long,
    ) : FileSource {
        override val relativePath: String = name
        private var known = false
        override val sizeBytes: Long? get() = if (known) realSize else null

        fun materialize() {
            known = true
        }

        override suspend fun openReadChannel(): ByteReadChannel = ByteReadChannel(ByteArray(realSize.toInt()))

        override fun close() = Unit
    }
}
