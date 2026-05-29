package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.presentation.PendingFilesSummary
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.FakeConnectionMonitor
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerUnreachableException
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.ReceiverWriteFailedException
import com.tubetoast.tether.transfer.TransferErrorReason
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferComponentTest {
    private val peer = Peer(
        id = PeerIdentity("test-peer"),
        device = Device(name = "TestDevice", host = "127.0.0.1", port = 8080),
    )

    private fun buildComponent(
        events: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 16),
        monitor: FakeConnectionMonitor = FakeConnectionMonitor(),
        pauseChannel: Channel<Unit>? = null,
        sendOneOverride: (suspend (FileSource, (Long, Long?) -> Unit) -> Unit)? = null,
        pendingFilesRepository: PendingFilesRepository? = null,
        onOpenPicker: () -> Unit = {},
        scope: kotlinx.coroutines.CoroutineScope,
    ): PeerTransferComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle)
        return PeerTransferComponent(
            componentContext = context,
            peer = peer,
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
            onShowDetails = {},
            scope = scope,
            pendingFilesRepository = pendingFilesRepository,
            onOpenPicker = onOpenPicker,
        )
    }

    @Test
    fun `startOutbound while Active is no-op`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val component = buildComponent(pauseChannel = pauseChannel, scope = backgroundScope)

        component.startOutbound(listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(component.state.value)
        val firstFile = (component.state.value as PeerTransferState.ActiveOutbound).currentFile

        component.startOutbound(listOf(FakeFileSource("b.txt", 200L)))
        runCurrent()

        val state = component.state.value as PeerTransferState.ActiveOutbound
        assertEquals(firstFile, state.currentFile)
    }

    @Test
    fun `onCancel transitions state to Cancelled`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val component = buildComponent(pauseChannel = pauseChannel, scope = backgroundScope)

        component.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(component.state.value)

        component.onCancel()
        runCurrent()

        assertIs<PeerTransferState.Cancelled>(component.state.value)
    }

    @Test
    fun `inbound Started Progress FileCompleted BatchCompleted produces Received`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(component.state.value)

        events.emit(ReceiveEvent.Progress("file.txt", 50L, 100L))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(component.state.value)

        events.emit(ReceiveEvent.FileCompleted("file.txt"))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(component.state.value)

        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 1))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Received>(state)
        assertEquals(1, state.received)
        assertEquals(1, state.total)
    }

    @Test
    fun `ReceiverSuspended event produces Error ReceiverSuspended`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.ReceiverSuspended)
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Error>(state)
        assertEquals(TransferErrorReason.ReceiverSuspended, state.reason)
    }

    @Test
    fun `onRetry after ReceiverSuspended does not restart transfer`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.ReceiverSuspended)
        runCurrent()

        val errorState = component.state.value
        assertIs<PeerTransferState.Error>(errorState)

        component.onRetry()
        runCurrent()

        assertIs<PeerTransferState.Error>(
            component.state.value,
            "State must remain Error when no retryable sources exist",
        )
    }

    @Test
    fun `BatchCompleted with failed CancelledByUser files produces ReceiverCancelled partial reason`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 2))
        runCurrent()
        events.emit(ReceiveEvent.Failed("skipped.txt", FailureReason.CancelledByUser))
        runCurrent()
        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 2))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Received>(state)
        assertIs<PartialOutcome.ReceiverCancelled>(state.partialReason)
    }

    @Test
    fun `BatchCompleted with partial receive and no cancellations produces ConnectionLost partial reason`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 2))
        runCurrent()
        events.emit(ReceiveEvent.BatchCompleted(received = 1, total = 2))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Received>(state)
        assertIs<PartialOutcome.ConnectionLost>(state.partialReason)
    }

    @Test
    fun `onRetry sends only failed files and skips already succeeded ones`() = runTest {
        val sendCalls = mutableMapOf("a.txt" to 0, "b.txt" to 0, "c.txt" to 0)
        val component = buildComponent(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                sendCalls[src.name] = (sendCalls[src.name] ?: 0) + 1
                if (src.name == "b.txt" && sendCalls["b.txt"] == 1) {
                    throw ReceiverWriteFailedException(507)
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        component.startOutbound(
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()

        val afterFirst = component.state.value
        assertIs<PeerTransferState.Sent>(afterFirst)
        assertEquals(2, afterFirst.sent)
        assertEquals(3, afterFirst.total)

        component.onRetry()
        runCurrent()

        assertEquals(1, sendCalls["a.txt"])
        assertEquals(2, sendCalls["b.txt"])
        assertEquals(1, sendCalls["c.txt"])

        val finalState = component.state.value
        assertIs<PeerTransferState.Sent>(finalState)
        assertNull(finalState.partialReason)
    }

    @Test
    fun `inbound ReceiveEvent Failed sets perFile entry to Failed`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("x.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.Progress("x.txt", 50L, 100L))
        runCurrent()
        events.emit(ReceiveEvent.Failed("x.txt", FailureReason.ReceiverWriteFailed(null)))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.ActiveInbound>(state)
        val xStatus = state.perFile.first { it.name == "x.txt" }
        assertIs<PerFileStatus.Failed>(xStatus)
        assertIs<FailureReason.ReceiverWriteFailed>(xStatus.reason)
    }

    @Test
    fun `inbound ConnectionLost transitions to Reconnecting Inbound`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        events.emit(ReceiveEvent.Progress("file.txt", 50L, 100L))
        runCurrent()
        events.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Reconnecting>(state)
        assertEquals(Direction.Inbound, state.direction)
    }

    @Test
    fun `inbound BatchCompleted with received less than total sets partialReason`() = runTest {
        val events = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val component = buildComponent(events = events, scope = backgroundScope)
        runCurrent()

        events.emit(ReceiveEvent.Started("file.txt", 3))
        runCurrent()
        events.emit(ReceiveEvent.BatchCompleted(received = 2, total = 3))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Received>(state)
        assertEquals(2, state.received)
        assertEquals(3, state.total)
        assertIs<PartialOutcome>(state.partialReason)
    }

    @Test
    fun `onRetryFile resends only the named file`() = runTest {
        val sendCalls = mutableMapOf("a.txt" to 0, "b.txt" to 0, "c.txt" to 0)
        val component = buildComponent(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                sendCalls[src.name] = (sendCalls[src.name] ?: 0) + 1
                if (src.name == "b.txt" && sendCalls["b.txt"] == 1) {
                    throw ReceiverWriteFailedException(507)
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        component.startOutbound(
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()
        assertIs<PeerTransferState.Sent>(component.state.value)

        component.onRetryFile("b.txt")
        runCurrent()

        assertEquals(1, sendCalls["a.txt"])
        assertEquals(2, sendCalls["b.txt"])
        assertEquals(1, sendCalls["c.txt"])
    }

    @Test
    fun `partial success with all PeerUnreachable failures produces PartialOutcome PeerUnreachable`() = runTest {
        val component = buildComponent(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                if (src.name == "b.txt") throw PeerUnreachableException()
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        component.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.PeerUnreachable, state.partialReason)
    }

    @Test
    fun `partial success with all ReceiverWriteFailed produces ReceiverWriteFailed partial`() = runTest {
        val component = buildComponent(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                if (src.name == "b.txt") throw ReceiverWriteFailedException(507)
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )

        component.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.ReceiverWriteFailed, state.partialReason)
    }

    @Test
    fun `partial success with heterogeneous failures produces PartialOutcome ConnectionLost`() = runTest {
        val component = buildComponent(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                when (src.name) {
                    "b.txt" -> throw PeerUnreachableException()
                    "c.txt" -> throw ReceiverWriteFailedException(507)
                    else -> onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                }
            },
        )

        component.startOutbound(
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Sent>(state)
        assertEquals(PartialOutcome.ConnectionLost, state.partialReason)
    }

    @Test
    fun `onCardClick with pending sources starts outbound and clears repo`() = runTest {
        val repo = PendingFilesRepository()
        val component = buildComponent(
            pendingFilesRepository = repo,
            scope = backgroundScope,
        )

        val sources = listOf(FakeFileSource("file.txt", 100L))
        repo.setPending(PendingFilesSummary(1, 100L), sources)

        component.onCardClick()
        runCurrent()

        assertIs<PeerTransferState.Sent>(component.state.value)
        assertNull(repo.summary.value)
    }

    @Test
    fun `onCardClick without pending sources invokes onOpenPicker`() = runTest {
        var pickerInvoked = false
        val component = buildComponent(
            onOpenPicker = { pickerInvoked = true },
            scope = backgroundScope,
        )

        component.onCardClick()

        assertTrue(pickerInvoked)
    }
}
