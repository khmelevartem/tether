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
        assertIs<PeerTransferState.Reconnecting>(engine.state.value)
        return engine
    }

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

        val initialState = assertIs<PeerTransferState.Reconnecting>(engine.state.value)
        assertEquals(3, initialState.remainingSeconds)

        advanceTimeBy(1_001)
        runCurrent()
        val afterOne = assertIs<PeerTransferState.Reconnecting>(engine.state.value)
        assertEquals(2, afterOne.remainingSeconds)

        advanceTimeBy(1_000)
        runCurrent()
        val afterTwo = assertIs<PeerTransferState.Reconnecting>(engine.state.value)
        assertEquals(1, afterTwo.remainingSeconds)
    }

    @Test
    fun `new Started event cancels countdown and transitions to ActiveInbound`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        advanceTimeBy(2_000)
        runCurrent()
        assertIs<PeerTransferState.Reconnecting>(engine.state.value)

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)

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
    fun `second ConnectionLost restarts countdown from full timeout`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val engine = engineInReconnecting(events, timeout = 5.seconds)

        advanceTimeBy(3_000)
        runCurrent()
        assertIs<PeerTransferState.Reconnecting>(engine.state.value)

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()

        val state = assertIs<PeerTransferState.Reconnecting>(engine.state.value)
        assertEquals(5, state.remainingSeconds)

        advanceTimeBy(6_000)
        runCurrent()

        assertIs<PeerTransferState.Error>(engine.state.value)
    }
}
