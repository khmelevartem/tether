package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.FakeConnectionMonitor
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `happy path all succeed returns AllSent and emits Sent`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        val outcome = makeSender().run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.AllSent>(outcome)
        val sent = emitted.last()
        assertIs<PeerTransferState.Sent>(sent)
        assertEquals(3, sent.sent)
        assertEquals(3, sent.total)
        assertNull(sent.partialReason)
    }

    @Test
    fun `happy path emits ActiveOutbound before Sent`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        makeSender().run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        val active = emitted.dropLast(1)
        assertTrue(active.isNotEmpty(), "Expected at least one ActiveOutbound before Sent")
        active.forEach { assertIs<PeerTransferState.ActiveOutbound>(it) }
    }

    @Test
    fun `cancel mid-file returns Cancelled with remaining files`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<PeerTransferState>()
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
        assertIs<PeerTransferState.Cancelled>(last)
        assertTrue(last.remaining.isNotEmpty(), "Expected remaining files")
    }

    @Test
    fun `unreadable file skips and rest succeed with FilesUnreadable partial reason`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        val sender = makeSender(
            sendOne = { src, onProgress ->
                if (src.name == "b.txt") throw UnreadableSourceException("b.txt")
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val sent = emitted.last()
        assertIs<PeerTransferState.Sent>(sent)
        assertEquals(2, sent.sent)
        val partial = sent.partialReason
        assertIs<PartialOutcome.FilesUnreadable>(partial)
        assertEquals(1, partial.count)

        val bFile = sent.perFile.first { it.name == "b.txt" }
        assertIs<PerFileStatus.Failed>(bFile)
        assertIs<FailureReason.Unreadable>(bFile.reason)
    }

    @Test
    fun `ReceiverWriteFailed file is marked failed and batch continues`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        val sender = makeSender(
            sendOne = { src, onProgress ->
                if (src.name == "b.txt") throw ReceiverWriteFailedException(507)
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val sent = emitted.last()
        assertIs<PeerTransferState.Sent>(sent)
        assertEquals(2, sent.sent)
        val bFile = sent.perFile.first { it.name == "b.txt" }
        assertIs<PerFileStatus.Failed>(bFile)
        val reason = bFile.reason
        assertIs<FailureReason.ReceiverWriteFailed>(reason)
        assertEquals(507, reason.httpStatus)
    }

    @Test
    fun `connection drop then reconnect resumes and produces AllSent`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<PeerTransferState>()
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
        assertIs<PeerTransferState.Sent>(last)
        assertEquals(3, last.sent)
        assertNull(last.partialReason)
    }

    @Test
    fun `connection drop then reconnect false produces Error NetworkLost`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<PeerTransferState>()
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
        assertIs<PeerTransferState.Error>(last)
        assertEquals(TransferErrorReason.NetworkLost, last.reason)
        assertEquals(1, last.sent)

        val failedFiles = last.perFile.filterIsInstance<PerFileStatus.Failed>()
        assertEquals(2, failedFiles.size)
        failedFiles.forEach { assertEquals(FailureReason.NetworkLost, it.reason) }
    }

    @Test
    fun `empty source list returns AllSent without emitting`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
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
        val emitted = mutableListOf<PeerTransferState>()
        val sender = makeSender(
            sendOne = { src, onProgress ->
                if (src.name == "b.txt") throw PeerUnreachableException()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val sent = emitted.last()
        assertIs<PeerTransferState.Sent>(sent)
        val bFile = sent.perFile.first { it.name == "b.txt" }
        assertIs<PerFileStatus.Failed>(bFile)
        assertEquals(FailureReason.PeerUnreachable, bFile.reason)
    }

    @Test
    fun `all files PeerUnreachable produces Error PeerUnreachable`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        val sender = makeSender(
            sendOne = { _, _ -> throw PeerUnreachableException() },
        )

        val outcome = sender.run(sources("a.txt", "b.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.Failed>(outcome)
        assertEquals(TransferErrorReason.PeerUnreachable, outcome.reason)
        val last = emitted.last()
        assertIs<PeerTransferState.Error>(last)
        assertEquals(TransferErrorReason.PeerUnreachable, last.reason)
    }

    @Test
    fun `all files ReceiverWriteFailed produces Error ReceiverWriteFailed`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        val sender = makeSender(
            sendOne = { _, _ -> throw ReceiverWriteFailedException(507) },
        )

        val outcome = sender.run(sources("a.txt", "b.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.Failed>(outcome)
        assertEquals(TransferErrorReason.ReceiverWriteFailed, outcome.reason)
        val last = emitted.last()
        assertIs<PeerTransferState.Error>(last)
        assertEquals(TransferErrorReason.ReceiverWriteFailed, last.reason)
    }

    @Test
    fun `mixed failure types with no successes produces Error AllFilesFailed`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
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
        assertIs<PeerTransferState.Error>(last)
        assertEquals(TransferErrorReason.AllFilesFailed, last.reason)
    }

    @Test
    fun `cancel marks remaining files with TransferCancelled reason`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val emitted = mutableListOf<PeerTransferState>()
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
        assertIs<PeerTransferState.Cancelled>(last)
        val cancelledFiles = last.perFile.filterIsInstance<PerFileStatus.Failed>()
        assertTrue(cancelledFiles.any { it.reason == FailureReason.TransferCancelled })
    }

    @Test
    fun `all files skipped by user produces SenderCancelled partial outcome`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        val sender = makeSender()

        val outcome = sender.run(sources("a.txt", "b.txt"), peer, skipPredicate = { true }) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val sent = emitted.last()
        assertIs<PeerTransferState.Sent>(sent)
        assertIs<PartialOutcome.SenderCancelled>(sent.partialReason)
    }

    @Test
    fun `ReceiverWriteFailed has SenderCancelled partialReason when mixed with cancelled`() = runTest {
        val emitted = mutableListOf<PeerTransferState>()
        val sender = makeSender(
            sendOne = { src, onProgress ->
                if (src.name == "b.txt") throw ReceiverWriteFailedException(507)
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        val outcome = sender.run(sources("a.txt", "b.txt", "c.txt"), peer) { emitted.add(it) }

        assertIs<BatchOutcome.PartialSent>(outcome)
        val sent = emitted.last()
        assertIs<PeerTransferState.Sent>(sent)
        val partial = sent.partialReason
        assertIs<PartialOutcome.ConnectionLost>(partial)
    }

    @Test
    fun `cancel before first file produces Cancelled with sent=0 and all files remaining`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val emitted = mutableListOf<PeerTransferState>()
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
        assertIs<PeerTransferState.Cancelled>(last)
        assertEquals(0, last.sent)
        assertEquals(srcs.size, last.remaining.size)
        assertTrue(last.remaining.containsAll(srcs.map { it.name }))
    }

    @Test
    fun `cancelCurrent arriving after sendOne completes does not mislabel file as CancelledByUser`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val emitted = mutableListOf<PeerTransferState>()
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
        val sent = emitted.filterIsInstance<PeerTransferState.Sent>().lastOrNull()
        assertTrue(
            sent != null &&
                sent.perFile.none {
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
        val emitted = mutableListOf<PeerTransferState>()
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
        assertIs<PeerTransferState.Cancelled>(last)
        assertTrue("file2.txt" !in last.remaining, "Skipped file must not appear in remaining")
    }
}
