package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.FakeConnectionMonitor
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerUnreachableException
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.ReceiverWriteFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferRepositoryTest {
    private val peer = PeerIdentity("test-peer")

    private fun buildRepository(
        events: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 16),
        monitor: FakeConnectionMonitor = FakeConnectionMonitor(),
        pauseChannel: Channel<Unit>? = null,
        sendOneOverride: (suspend (FileSource, (Long, Long?) -> Unit) -> Unit)? = null,
        scope: kotlinx.coroutines.CoroutineScope,
    ): PeerTransferRepositoryImpl = PeerTransferRepositoryImpl(
        batchSenderFactory = {
            BatchSender(
                sendOne = sendOneOverride ?: { src, onProgress ->
                    pauseChannel?.receive()
                    onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                },
                connectionMonitor = monitor,
                progressThrottle = 100.milliseconds,
                dispatcher = Dispatchers.Unconfined,
            )
        },
        inboundEvents = events,
        scope = scope,
    )

    @Test
    fun `startOutbound while Active is no-op`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val repo = buildRepository(pauseChannel = pauseChannel, scope = backgroundScope)

        repo.startOutbound(peer, listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(repo.observe(peer).value)
        val firstFile = (repo.observe(peer).value as PeerTransferState.ActiveOutbound).currentFile

        repo.startOutbound(peer, listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        val state = repo.observe(peer).value as PeerTransferState.ActiveOutbound
        assertEquals(firstFile, state.currentFile)
    }

    @Test
    fun `cancel transitions state to Cancelled`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val repo = buildRepository(pauseChannel = pauseChannel, scope = backgroundScope)

        repo.startOutbound(peer, listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(repo.observe(peer).value)

        repo.cancel(peer)
        runCurrent()

        assertIs<PeerTransferState.Cancelled>(repo.observe(peer).value)
    }

    @Test
    fun `retry sends only failed files and skips already succeeded ones`() = runTest {
        val sendCalls = mutableMapOf("a.txt" to 0, "b.txt" to 0, "c.txt" to 0)
        val repo = buildRepository(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                sendCalls[src.name] = (sendCalls[src.name] ?: 0) + 1
                if (src.name == "b.txt" && sendCalls["b.txt"] == 1) {
                    throw ReceiverWriteFailedException(507)
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        repo.startOutbound(
            peer,
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()

        val afterFirst = repo.observe(peer).value
        assertIs<PeerTransferState.Sent>(afterFirst)
        assertEquals(2, afterFirst.sent)
        assertEquals(3, afterFirst.total)

        repo.retry(peer)
        runCurrent()

        assertEquals(1, sendCalls["a.txt"])
        assertEquals(2, sendCalls["b.txt"])
        assertEquals(1, sendCalls["c.txt"])

        val finalState = repo.observe(peer).value
        assertIs<PeerTransferState.Sent>(finalState)
        assertNull(finalState.partialReason)
    }

    @Test
    fun `retry after ReceiverSuspended does not restart transfer`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val repo = buildRepository(events = events, scope = backgroundScope)

        repo.retry(peer)
        runCurrent()

        assertIs<PeerTransferState.Idle>(repo.observe(peer).value)
    }

    @Test
    fun `retryFile resends only the named file`() = runTest {
        val sendCalls = mutableMapOf("a.txt" to 0, "b.txt" to 0, "c.txt" to 0)
        val repo = buildRepository(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                sendCalls[src.name] = (sendCalls[src.name] ?: 0) + 1
                if (src.name == "b.txt" && sendCalls["b.txt"] == 1) {
                    throw ReceiverWriteFailedException(507)
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        repo.startOutbound(
            peer,
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()
        assertIs<PeerTransferState.Sent>(repo.observe(peer).value)

        repo.retryFile(peer, "b.txt")
        runCurrent()

        assertEquals(1, sendCalls["a.txt"])
        assertEquals(2, sendCalls["b.txt"])
        assertEquals(1, sendCalls["c.txt"])
    }

    @Test
    fun `partial success with all PeerUnreachable failures produces PartialOutcome PeerUnreachable`() = runTest {
        val repo = buildRepository(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                if (src.name == "b.txt") throw PeerUnreachableException()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        repo.startOutbound(peer, listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()

        val state = repo.observe(peer).value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.PeerUnreachable, state.partialReason)
    }

    @Test
    fun `partial success with all ReceiverWriteFailed produces ReceiverWriteFailed partial`() = runTest {
        val repo = buildRepository(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                if (src.name == "b.txt") throw ReceiverWriteFailedException(507)
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        repo.startOutbound(peer, listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()

        val state = repo.observe(peer).value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.ReceiverWriteFailed, state.partialReason)
    }

    @Test
    fun `partial success with heterogeneous failures produces PartialOutcome ConnectionLost`() = runTest {
        val repo = buildRepository(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                when (src.name) {
                    "b.txt" -> throw PeerUnreachableException()
                    "c.txt" -> throw ReceiverWriteFailedException(507)
                    else -> onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                }
            },
        )

        repo.startOutbound(
            peer,
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()

        val state = repo.observe(peer).value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.ConnectionLost, state.partialReason)
    }
}
