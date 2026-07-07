package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferEngineInboundTest {
    private val peer = PeerIdentity("test-peer")

    private fun buildEngine(
        events: MutableSharedFlow<ReceiveEvent>,
        scope: CoroutineScope,
        timeout: Duration = ReconnectionTimeout.DEFAULT,
    ): PeerTransferEngine = PeerTransferEngine(
        peer = peer,
        batchSenderFactory = fakeBatchSender(),
        inboundEvents = events,
        reconnectionTimeout = timeout,
        scope = scope,
        peerPreferencesStore = FakePeerPreferencesStore(),
        cancelBatch = { },
    )

    private suspend fun TestScope.engineInReconnecting(
        events: MutableSharedFlow<ReceiveEvent>,
        timeout: Duration,
    ): PeerTransferEngine {
        val engine = buildEngine(events, backgroundScope, timeout)
        runCurrent()
        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()
        val state = assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
        assertIs<PeerTransferState.InboundLink.Reconnecting>(state.link)
        return engine
    }

    private fun PeerTransferState.linkOrNull(): PeerTransferState.InboundLink? =
        (this as? PeerTransferState.ActiveInbound)?.link

    @Test
    fun `ConnectionLost starts countdown and Error NetworkLost fires on expiry`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        advanceTimeBy(6_000)
        runCurrent()

        val state = engine.state.value
        assertIs<PeerTransferState.Error>(state)
        assertEquals(TransferErrorReason.NetworkLost, state.reason)
    }

    @Test
    fun `ConnectionLost countdown ticks remainingSeconds downward`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 3.seconds)

        val initialLink = assertIs<PeerTransferState.InboundLink.Reconnecting>(engine.state.value.linkOrNull())
        assertEquals(3, initialLink.remainingSeconds)

        advanceTimeBy(1_001)
        runCurrent()
        val afterOne = assertIs<PeerTransferState.InboundLink.Reconnecting>(engine.state.value.linkOrNull())
        assertEquals(2, afterOne.remainingSeconds)

        advanceTimeBy(1_000)
        runCurrent()
        val afterTwo = assertIs<PeerTransferState.InboundLink.Reconnecting>(engine.state.value.linkOrNull())
        assertEquals(1, afterTwo.remainingSeconds)
    }

    @Test
    fun `new Started event cancels countdown and transitions to ActiveInbound Connected`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        advanceTimeBy(2_000)
        runCurrent()
        assertIs<PeerTransferState.InboundLink.Reconnecting>(engine.state.value.linkOrNull())

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        assertEquals(PeerTransferState.InboundLink.Connected, engine.state.value.linkOrNull())

        advanceTimeBy(10_000)
        runCurrent()

        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
    }

    @Test
    fun `BatchCompleted event cancels reconnect countdown`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 1))
        runCurrent()
        assertIs<PeerTransferState.Received>(engine.state.value)

        advanceTimeBy(10_000)
        runCurrent()

        assertIs<PeerTransferState.Received>(engine.state.value)
    }

    @Test
    fun `ConnectionLost then SenderCancelled BatchCompleted reaches Received without NetworkLost`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        events.emit(
            ReceiveEvent.BatchCompleted(received = 1, total = 3, partialReason = PartialOutcome.SenderCancelled),
        )
        runCurrent()

        val state = assertIs<PeerTransferState.Received>(engine.state.value)
        assertEquals(PartialOutcome.SenderCancelled, state.partialReason)

        advanceTimeBy(10_000)
        runCurrent()

        assertIs<PeerTransferState.Received>(engine.state.value)
    }

    @Test
    fun `ConnectionLost after terminal Received is a no-op`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events, backgroundScope, timeout = 5.seconds)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 3))
        runCurrent()
        events.emit(
            ReceiveEvent.BatchCompleted(received = 1, total = 3, partialReason = PartialOutcome.ReceiverCancelled),
        )
        runCurrent()
        assertIs<PeerTransferState.Received>(engine.state.value)

        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 1))
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertIs<PeerTransferState.Received>(engine.state.value)
    }

    @Test
    fun `reconnect then FileCompleted restores ActiveInbound Connected and marks the file done`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        events.emit(ReceiveEvent.FileCompleted("file.txt"))
        runCurrent()

        val state = assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
        assertEquals(PeerTransferState.InboundLink.Connected, state.link)
        val fileStatus = state.perFile.first { it.name == "file.txt" }
        assertIs<PerFileStatus.Done>(fileStatus)
    }

    @Test
    fun `double ConnectionLost keeps the received count and does not nest Reconnecting`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        advanceTimeBy(3_000)
        runCurrent()

        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()

        val state = assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
        val link = assertIs<PeerTransferState.InboundLink.Reconnecting>(state.link)
        assertEquals(5, link.remainingSeconds)
        assertEquals("file.txt", state.currentFile)
    }

    @Test
    fun `BatchCompleted after reconnect terminates in Received not stuck Reconnecting`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 1))
        runCurrent()

        val state = assertIs<PeerTransferState.Received>(engine.state.value)
        assertEquals(1, state.received)

        advanceTimeBy(10_000)
        runCurrent()
        assertIs<PeerTransferState.Received>(engine.state.value)
    }

    @Test
    fun `second ConnectionLost restarts countdown from full timeout`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        advanceTimeBy(3_000)
        runCurrent()
        assertIs<PeerTransferState.InboundLink.Reconnecting>(engine.state.value.linkOrNull())

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()

        val link = assertIs<PeerTransferState.InboundLink.Reconnecting>(engine.state.value.linkOrNull())
        assertEquals(5, link.remainingSeconds)

        advanceTimeBy(6_000)
        runCurrent()

        assertIs<PeerTransferState.Error>(engine.state.value)
    }

    @Test
    fun `silent sender after last file finalizes to partial Received`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events, backgroundScope, timeout = 5.seconds)
        runCurrent()

        events.emit(ReceiveEvent.Started("file1.txt", 3))
        runCurrent()
        events.emit(ReceiveEvent.FileCompleted("file1.txt"))
        runCurrent()
        events.emit(ReceiveEvent.FileCompleted("file2.txt"))
        runCurrent()

        advanceTimeBy(INBOUND_IDLE_GRACE.inWholeMilliseconds + 1_000)
        runCurrent()

        val state = assertIs<PeerTransferState.Received>(engine.state.value)
        assertEquals(2, state.received)
        assertEquals(3, state.total)
        assertEquals(PartialOutcome.ConnectionLost, state.partialReason)
    }

    @Test
    fun `fully delivered but batch-end lost finalizes to complete Received`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events, backgroundScope, timeout = 5.seconds)
        runCurrent()

        events.emit(ReceiveEvent.Started("file1.txt", 2))
        runCurrent()
        events.emit(ReceiveEvent.FileCompleted("file1.txt"))
        runCurrent()
        events.emit(ReceiveEvent.FileCompleted("file2.txt"))
        runCurrent()

        advanceTimeBy(INBOUND_IDLE_GRACE.inWholeMilliseconds + 1_000)
        runCurrent()

        val state = assertIs<PeerTransferState.Received>(engine.state.value)
        assertEquals(2, state.received)
        assertEquals(2, state.total)
        assertEquals(null, state.partialReason)
    }

    @Test
    fun `restore then silence still terminates`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        events.emit(ReceiveEvent.Progress(name = "file.txt", receivedBytes = 10L, totalBytes = 100L))
        runCurrent()
        assertEquals(PeerTransferState.InboundLink.Connected, engine.state.value.linkOrNull())

        advanceTimeBy(INBOUND_IDLE_GRACE.inWholeMilliseconds + 1_000)
        runCurrent()

        assertIs<PeerTransferState.Received>(engine.state.value)
    }

    @Test
    fun `Progress rearms inactivity timer`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events, backgroundScope, timeout = 5.seconds)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()

        advanceTimeBy(INBOUND_IDLE_GRACE.inWholeMilliseconds - 1_000)
        runCurrent()
        events.emit(ReceiveEvent.Progress(name = "file.txt", receivedBytes = 10L, totalBytes = 100L))
        runCurrent()

        advanceTimeBy(INBOUND_IDLE_GRACE.inWholeMilliseconds - 1_000)
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)

        advanceTimeBy(2_000)
        runCurrent()
        assertIs<PeerTransferState.Received>(engine.state.value)
    }

    @Test
    fun `ConnectionLost expiry is Error while silent-sender expiry is Received`() = runTest {
        val lostEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val lostEngine = engineInReconnecting(lostEvents, timeout = 5.seconds)
        advanceTimeBy(6_000)
        runCurrent()
        assertIs<PeerTransferState.Error>(lostEngine.state.value)

        val silentEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val silentEngine = buildEngine(silentEvents, backgroundScope, timeout = 5.seconds)
        runCurrent()
        silentEvents.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()

        advanceTimeBy(INBOUND_IDLE_GRACE.inWholeMilliseconds + 1_000)
        runCurrent()
        assertIs<PeerTransferState.Received>(silentEngine.state.value)
    }

    @Test
    fun `inactivity expiry after terminal state is a no-op`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = buildEngine(events, backgroundScope, timeout = 5.seconds)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 1))
        runCurrent()
        val state = assertIs<PeerTransferState.Received>(engine.state.value)

        advanceTimeBy(INBOUND_IDLE_GRACE.inWholeMilliseconds + 1_000)
        runCurrent()

        assertEquals(state, engine.state.value)
    }
}
