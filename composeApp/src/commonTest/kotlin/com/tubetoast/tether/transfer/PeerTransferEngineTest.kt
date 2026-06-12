package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferEngineTest {
    private val peer = PeerIdentity("test-peer")

    private fun buildEngine(
        events: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 16),
        pauseChannel: Channel<Unit>? = null,
        sendOneOverride: (suspend (FileSource, (Long, Long?) -> Unit) -> Unit)? = null,
        scope: kotlinx.coroutines.CoroutineScope,
    ): PeerTransferEngine = PeerTransferEngine(
        peer = peer,
        batchSenderFactory = fakeBatchSender(sendOneOverride = sendOneOverride, pauseChannel = pauseChannel),
        inboundEvents = events,
        scope = scope,
        peerPreferencesStore = FakePeerPreferencesStore(),
    )

    private suspend fun TestScope.engineInActiveInbound(
        events: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 16),
    ): PeerTransferEngine {
        val engine = buildEngine(events = events, scope = backgroundScope)
        runCurrent()
        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
        return engine
    }

    private suspend fun TestScope.engineInReconnecting(
        events: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 16),
    ): PeerTransferEngine {
        val engine = engineInActiveInbound(events)
        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()
        assertIs<PeerTransferState.Reconnecting>(engine.state.value)
        return engine
    }

    private suspend fun TestScope.engineInReceived(
        events: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 16),
    ): PeerTransferEngine {
        val engine = engineInActiveInbound(events)
        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 1))
        runCurrent()
        assertIs<PeerTransferState.Received>(engine.state.value)
        return engine
    }

    private suspend fun TestScope.engineInError(
        events: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 16),
    ): PeerTransferEngine {
        val engine = engineInActiveInbound(events)
        events.emit(ReceiveEvent.ReceiverSuspended)
        runCurrent()
        assertIs<PeerTransferState.Error>(engine.state.value)
        return engine
    }

    private suspend fun TestScope.engineInCancelled(): PeerTransferEngine {
        val pauseChannel = Channel<Unit>(0)
        val engine = buildEngine(pauseChannel = pauseChannel, scope = backgroundScope)
        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()
        engine.onCancel()
        runCurrent()
        assertIs<PeerTransferState.Cancelled>(engine.state.value)
        return engine
    }

    private suspend fun TestScope.engineInSent(): PeerTransferEngine {
        val engine = buildEngine(scope = backgroundScope)
        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.Sent>(engine.state.value)
        return engine
    }

    @Test
    fun `startOutbound while Active is no-op`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val engine = buildEngine(pauseChannel = pauseChannel, scope = backgroundScope)

        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()
        val firstState = assertIs<PeerTransferState.ActiveOutbound.Sending>(engine.state.value)
        val firstFile = firstState.currentFile

        engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        val state = assertIs<PeerTransferState.ActiveOutbound.Sending>(engine.state.value)
        assertEquals(firstFile, state.currentFile)
    }

    @Test
    fun `onCancel transitions state to Cancelled`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val engine = buildEngine(pauseChannel = pauseChannel, scope = backgroundScope)

        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(engine.state.value)

        engine.onCancel()
        runCurrent()

        assertIs<PeerTransferState.Cancelled>(engine.state.value)
    }

    @Test
    fun `cancel during Claimed phase transitions to Cancelled`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val engine = buildEngine(pauseChannel = pauseChannel, scope = backgroundScope)

        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        assertIs<PeerTransferState.ActiveOutbound.Claimed>(engine.state.value)

        engine.onCancel()
        runCurrent()

        assertIs<PeerTransferState.Cancelled>(engine.state.value)
    }

    @Test
    fun `cancel during Claimed phase produces retryable Cancelled`() = runTest {
        var factoryInvocations = 0
        val engine = PeerTransferEngine(
            peer = peer,
            batchSenderFactory = {
                factoryInvocations++
                fakeBatchSender()()
            },
            inboundEvents = MutableSharedFlow(),
            scope = backgroundScope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )

        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        assertIs<PeerTransferState.ActiveOutbound.Claimed>(engine.state.value)

        engine.onCancel()
        runCurrent()

        assertIs<PeerTransferState.Cancelled>(engine.state.value)
        // First launch was cancelled before its body executed — factory never called.
        assertEquals(0, factoryInvocations)

        engine.onRetryOutbound()
        runCurrent()

        assertEquals(1, factoryInvocations)
        assertIs<PeerTransferState.Sent>(engine.state.value)
    }

    @Test
    fun `lazy source maps to Preparing state until the first byte`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val engine = buildEngine(pauseChannel = pauseChannel, scope = backgroundScope)

        engine.startOutbound(listOf(FakeFileSource("photo.jpg", 100L, materializesLazily = true)))
        runCurrent()

        // First emit for a lazy source carries preparing=true; the engine promotes it to Preparing.
        assertIs<PeerTransferState.ActiveOutbound.Preparing>(engine.state.value)
    }

    @Test
    fun `onCancelFile marks a queued file failed while in Preparing`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val engine = buildEngine(pauseChannel = pauseChannel, scope = backgroundScope)

        engine.startOutbound(
            listOf(
                FakeFileSource("photo.jpg", 100L, materializesLazily = true),
                FakeFileSource("queued.jpg", 100L),
            ),
        )
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound.Preparing>(engine.state.value)

        engine.onCancelFile("queued.jpg")
        runCurrent()

        val state = assertIs<PeerTransferState.ActiveOutbound.Preparing>(engine.state.value)
        assertIs<PerFileStatus.Failed>(state.perFile.first { it.name == "queued.jpg" })
        assertEquals(1, state.skippedCount)
    }

    @Test
    fun `inbound Started Progress FileCompleted BatchCompleted produces Received`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInActiveInbound(events)

        events.emit(ReceiveEvent.Progress("file.txt", 50L, 100L))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)

        events.emit(ReceiveEvent.FileCompleted("file.txt"))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)

        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 1))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Received>(state)
        assertEquals(1, state.received)
        assertEquals(1, state.total)
    }

    @Test
    fun `ReceiverSuspended event produces Error ReceiverSuspended`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInError(events)

        val state = engine.state.value
        assertIs<PeerTransferState.Error>(state)
        assertEquals(TransferErrorReason.ReceiverSuspended, state.reason)
    }

    @Test
    fun `onRetryOutbound after ReceiverSuspended does not restart transfer`() = runTest {
        val engine = engineInError()

        engine.onRetryOutbound()
        runCurrent()

        assertIs<PeerTransferState.Error>(
            engine.state.value,
            "State must remain Error when no retryable sources exist",
        )
    }

    @Test
    fun `BatchCompleted with failed CancelledByUser files produces ReceiverCancelled partial reason`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInActiveInbound(events)

        events.emit(ReceiveEvent.Failed("skipped.txt", FailureReason.CancelledByUser))
        runCurrent()
        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 2))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Received>(state)
        assertIs<PartialOutcome.ReceiverCancelled>(state.partialReason)
    }

    @Test
    fun `BatchCompleted with partial receive and no cancellations produces ConnectionLost partial reason`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 2))
        runCurrent()
        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 2))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Received>(state)
        assertIs<PartialOutcome.ConnectionLost>(state.partialReason)
    }

    @Test
    fun `onRetryOutbound sends only failed files and skips already succeeded ones`() = runTest {
        val sendCalls = mutableMapOf("a.txt" to 0, "b.txt" to 0, "c.txt" to 0)
        val engine = buildEngine(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                sendCalls[src.name] = (sendCalls[src.name] ?: 0) + 1
                if (src.name == "b.txt" && sendCalls["b.txt"] == 1) {
                    throw ReceiverWriteFailedException(507)
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        engine.startOutbound(
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()

        val afterFirst = engine.state.value
        assertIs<PeerTransferState.Sent>(afterFirst)
        assertEquals(2, afterFirst.sent)
        assertEquals(3, afterFirst.total)

        engine.onRetryOutbound()
        runCurrent()

        assertEquals(1, sendCalls["a.txt"])
        assertEquals(2, sendCalls["b.txt"])
        assertEquals(1, sendCalls["c.txt"])

        val finalState = engine.state.value
        assertIs<PeerTransferState.Sent>(finalState)
        assertNull(finalState.partialReason)
    }

    @Test
    fun `inbound ReceiveEvent Failed sets perFile entry to Failed`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("x.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.Progress("x.txt", 50L, 100L))
        runCurrent()
        events.emit(ReceiveEvent.Failed("x.txt", FailureReason.ReceiverWriteFailed(null)))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.ActiveInbound>(state)
        val xStatus = state.perFile.first { it.name == "x.txt" }
        assertIs<PerFileStatus.Failed>(xStatus)
        assertIs<FailureReason.ReceiverWriteFailed>(xStatus.reason)
    }

    @Test
    fun `inbound ConnectionLost transitions to Reconnecting Inbound`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInActiveInbound(events)

        events.emit(ReceiveEvent.Progress("file.txt", 50L, 100L))
        runCurrent()
        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Reconnecting>(state)
        assertEquals(Direction.Inbound, state.direction)
    }

    @Test
    fun `inbound BatchCompleted with received less than total sets partialReason`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 3))
        runCurrent()
        events.emit(ReceiveEvent.BatchCompleted(received = 2, total = 3))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Received>(state)
        assertEquals(2, state.received)
        assertEquals(3, state.total)
        assertIs<PartialOutcome>(state.partialReason)
    }

    @Test
    fun `onRetryFile resends only the named file`() = runTest {
        val sendCalls = mutableMapOf("a.txt" to 0, "b.txt" to 0, "c.txt" to 0)
        val engine = buildEngine(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                sendCalls[src.name] = (sendCalls[src.name] ?: 0) + 1
                if (src.name == "b.txt" && sendCalls["b.txt"] == 1) {
                    throw ReceiverWriteFailedException(507)
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        engine.startOutbound(
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()
        assertIs<PeerTransferState.Sent>(engine.state.value)

        engine.onRetryFile("b.txt")
        runCurrent()

        assertEquals(1, sendCalls["a.txt"])
        assertEquals(2, sendCalls["b.txt"])
        assertEquals(1, sendCalls["c.txt"])
    }

    @Test
    fun `partial success with all PeerUnreachable failures produces PartialOutcome PeerUnreachable`() = runTest {
        val engine = buildEngine(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                if (src.name == "b.txt") throw PeerUnreachableException()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.PeerUnreachable, state.partialReason)
    }

    @Test
    fun `partial success with all ReceiverWriteFailed produces ReceiverWriteFailed partial`() = runTest {
        val engine = buildEngine(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                if (src.name == "b.txt") throw ReceiverWriteFailedException(507)
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.ReceiverWriteFailed, state.partialReason)
    }

    @Test
    fun `partial success with heterogeneous failures produces PartialOutcome ConnectionLost`() = runTest {
        val engine = buildEngine(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                when (src.name) {
                    "b.txt" -> throw PeerUnreachableException()
                    "c.txt" -> throw ReceiverWriteFailedException(507)
                    else -> onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                }
            },
        )

        engine.startOutbound(
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.ConnectionLost, state.partialReason)
    }

    @Test
    fun `concurrent startOutbound calls produce one launchBatch`() = runTest {
        var factoryInvocations = 0
        val engine = PeerTransferEngine(
            peer = peer,
            batchSenderFactory = {
                factoryInvocations++
                fakeBatchSender()()
            },
            inboundEvents = MutableSharedFlow(),
            scope = backgroundScope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )

        val sourcesA = listOf(FakeFileSource("a.txt", 100L))
        val sourcesB = listOf(FakeFileSource("b.txt", 200L))
        engine.startOutbound(sourcesA)
        engine.startOutbound(sourcesB)
        runCurrent()

        assertEquals(1, factoryInvocations)
        val state = engine.state.value as PeerTransferState.Sent
        assertEquals("a.txt", state.perFile.first().name)
    }

    @Test
    fun `startOutbound returns true when Idle`() = runTest {
        val engine = buildEngine(scope = backgroundScope)

        val result = engine.startOutbound(listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()

        assertTrue(result)
        assertIs<PeerTransferState.Sent>(engine.state.value)
    }

    @Test
    fun `startOutbound returns false when ActiveOutbound`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val engine = buildEngine(pauseChannel = pauseChannel, scope = backgroundScope)

        engine.startOutbound(listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(engine.state.value)

        val result = engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        assertFalse(result)
        assertIs<PeerTransferState.ActiveOutbound>(engine.state.value)
    }

    @Test
    fun `startOutbound returns false when ActiveInbound`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInActiveInbound(events)

        val result = engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        assertFalse(result)
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
    }

    @Test
    fun `startOutbound returns false when Reconnecting`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events)

        val result = engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        assertFalse(result)
        assertIs<PeerTransferState.Reconnecting>(engine.state.value)
    }

    @Test
    fun `startOutbound returns false when Sent`() = runTest {
        val engine = engineInSent()

        val result = engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        assertFalse(result)
        assertIs<PeerTransferState.Sent>(engine.state.value)
    }

    @Test
    fun `startOutbound returns false when Received`() = runTest {
        val engine = engineInReceived()

        val result = engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        assertFalse(result)
        assertIs<PeerTransferState.Received>(engine.state.value)
    }

    @Test
    fun `startOutbound returns false when Error`() = runTest {
        val engine = engineInError()

        val result = engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        assertFalse(result)
        assertIs<PeerTransferState.Error>(engine.state.value)
    }

    @Test
    fun `startOutbound returns false when Cancelled`() = runTest {
        val engine = engineInCancelled()

        val result = engine.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        assertFalse(result)
        assertIs<PeerTransferState.Cancelled>(engine.state.value)
    }

    @Test
    fun `onDismiss from Sent returns to Idle`() = runTest {
        val engine = engineInSent()

        engine.onDismiss()
        runCurrent()

        assertIs<PeerTransferState.Idle>(engine.state.value)
    }
}
