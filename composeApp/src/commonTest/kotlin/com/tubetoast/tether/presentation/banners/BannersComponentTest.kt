package com.tubetoast.tether.presentation.banners

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.peer.FakePeersRepository
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.DefaultTransferActivityTracker
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.NoOpTransferActivityTracker
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PendingFilesSummary
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.TransferActivityTracker
import com.tubetoast.tether.transfer.fakeBatchSender
import com.tubetoast.tether.transfer.fakePeerTransferEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BannersComponentTest {
    private val peerId = PeerIdentity("test-peer")
    private val peerName = "TestDevice"

    private fun buildComponent(
        repo: PendingFilesRepository = PendingFilesRepository(),
        peersRepository: FakePeersRepository = FakePeersRepository(),
        engineRegistry: PeerTransferEngineRegistry? = null,
        conflictRelay: PeerConflictRelay = PeerConflictRelay(),
        transferActivityTracker: TransferActivityTracker = NoOpTransferActivityTracker,
        ownDeviceType: DeviceType = DeviceType.Android,
        coroutineScope: CoroutineScope,
    ): BannersComponent {
        val lifecycle = LifecycleRegistry().also { it.resume() }
        return BannersComponent(
            componentContext = DefaultComponentContext(lifecycle),
            pendingFilesRepository = repo,
            peersRepository = peersRepository,
            engineRegistry = engineRegistry ?: fakePeerTransferEngineRegistry(coroutineScope),
            conflictRelay = conflictRelay,
            transferActivityTracker = transferActivityTracker,
            ownDeviceType = ownDeviceType,
            coroutineScope = coroutineScope,
        )
    }

    private fun fakePeersRepository() = FakePeersRepository(
        MutableStateFlow(listOf(Peer(peerId, Device(peerName, "127.0.0.1", 8080)))),
    )

    private fun pausedRegistry(pauseChannel: Channel<Unit>, scope: CoroutineScope) =
        PeerTransferEngineRegistry(
            appScope = scope,
            engineFactory = { id, engineScope ->
                PeerTransferEngine(
                    peer = id,
                    batchSenderFactory = fakeBatchSender(pauseChannel = pauseChannel),
                    inboundEvents = MutableSharedFlow(),
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )

    @Test
    fun `onCancelPending clears repo`() = runTest {
        val repo = PendingFilesRepository()
        val component = buildComponent(repo, coroutineScope = backgroundScope)

        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))
        component.onCancelPending()

        assertNull(repo.pending.value?.summary)
    }

    @Test
    fun `showForegroundConstraint is true when active and device is iOS`() = runTest(UnconfinedTestDispatcher()) {
        val tracker = DefaultTransferActivityTracker(backgroundScope)
        val component = buildComponent(
            transferActivityTracker = tracker,
            ownDeviceType = DeviceType.Ios,
            coroutineScope = backgroundScope,
        )
        tracker.withActiveTransfer {
            assertTrue(component.showForegroundConstraint.value)
        }
    }

    @Test
    fun `showForegroundConstraint is false when active but device is not iOS`() = runTest(UnconfinedTestDispatcher()) {
        val tracker = DefaultTransferActivityTracker(backgroundScope)
        val component = buildComponent(
            transferActivityTracker = tracker,
            ownDeviceType = DeviceType.Android,
            coroutineScope = backgroundScope,
        )
        tracker.withActiveTransfer {
            assertFalse(component.showForegroundConstraint.value)
        }
    }

    @Test
    fun `showForegroundConstraint is false when inactive and device is iOS`() = runTest(UnconfinedTestDispatcher()) {
        val tracker = DefaultTransferActivityTracker(backgroundScope)
        val component = buildComponent(
            transferActivityTracker = tracker,
            ownDeviceType = DeviceType.Ios,
            coroutineScope = backgroundScope,
        )
        assertFalse(component.showForegroundConstraint.value)
    }

    @Test
    fun `dropFeedback is true during feedback window and false after`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        component.onDropDuringActiveTransfer()
        runCurrent()

        assertTrue(component.dropFeedback.value)
        advanceTimeBy(3_100L)

        assertFalse(component.dropFeedback.value)
    }

    @Test
    fun `second onDropDuringActiveTransfer within window restarts the timer`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        component.onDropDuringActiveTransfer()
        runCurrent()
        advanceTimeBy(2_500L)
        assertTrue(component.dropFeedback.value)

        component.onDropDuringActiveTransfer()
        runCurrent()
        advanceTimeBy(2_500L)
        assertTrue(component.dropFeedback.value, "flag should still be true — second call restarted the window")

        advanceTimeBy(600L)
        assertFalse(component.dropFeedback.value)
    }

    @Test
    fun `pendingBanner is Hidden when no pending files`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        runCurrent()

        assertIs<PendingOutboundBannerState.Hidden>(component.pendingBanner.value)
    }

    @Test
    fun `pendingBanner is Default when pending files exist and no conflict`() = runTest {
        val repo = PendingFilesRepository()
        val component = buildComponent(repo, coroutineScope = backgroundScope)
        val sources = listOf(FakeFileSource("a.txt", 512L), FakeFileSource("b.txt", 512L))
        repo.setPending(sources)

        runCurrent()

        val state = assertIs<PendingOutboundBannerState.Default>(component.pendingBanner.value)
        assertEquals(PendingFilesSummary.from(sources), state.summary)
    }

    @Test
    fun `busy tap with no pending does not poison selectedConflictPeer`() = runTest {
        val relay = PeerConflictRelay()
        val pauseChannel = Channel<Unit>(0)
        val registry = pausedRegistry(pauseChannel, backgroundScope)
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(registry.engineFor(peerId).state.value)

        val repo = PendingFilesRepository()
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )

        relay.reportBusyTap(peerId)
        runCurrent()

        repo.setPending(listOf(FakeFileSource("share.txt", 100L)))
        runCurrent()

        val state = assertIs<PendingOutboundBannerState.Default>(component.pendingBanner.value)
        assertEquals(PendingFilesSummary(1, 100L), state.summary)
    }

    @Test
    fun `busy tap transitions banner to BusyPeer`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val pauseChannel = Channel<Unit>(0)
        val registry = pausedRegistry(pauseChannel, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        val state = assertIs<PendingOutboundBannerState.BusyPeer>(component.pendingBanner.value)
        assertEquals(PendingFilesSummary(1, 100L), state.summary)
        assertEquals(peerName, state.peerName)
    }

    @Test
    fun `BusyPeer shown when engine is ActiveInbound via ReceiveEvent Started`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = fakePeerTransferEngineRegistry(backgroundScope, inboundEvents)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId)
        runCurrent()
        inboundEvents.emit(ReceiveEvent.Started(currentFile = "remote.txt", totalFiles = 1))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        val state = assertIs<PendingOutboundBannerState.BusyPeer>(component.pendingBanner.value)
        assertEquals(PendingFilesSummary(1, 100L), state.summary)
        assertEquals(peerName, state.peerName)
    }

    @Test
    fun `BusyPeer shown when engine is Reconnecting via ReceiveEvent ConnectionLost`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = fakePeerTransferEngineRegistry(backgroundScope, inboundEvents)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId)
        runCurrent()
        inboundEvents.emit(ReceiveEvent.Started(currentFile = "remote.txt", totalFiles = 1))
        inboundEvents.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        runCurrent()
        assertIs<PeerTransferState.Reconnecting>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        assertIs<PendingOutboundBannerState.BusyPeer>(component.pendingBanner.value)
    }

    @Test
    fun `banner resets to Hidden when pending is cleared`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val pauseChannel = Channel<Unit>(0)
        val registry = pausedRegistry(pauseChannel, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()
        assertIs<PendingOutboundBannerState.BusyPeer>(component.pendingBanner.value)

        repo.clear()
        runCurrent()
        runCurrent()

        assertIs<PendingOutboundBannerState.Hidden>(component.pendingBanner.value)
    }

    @Test
    fun `selectedConflictPeer resets to null when engine returns to Idle`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val pauseChannel = Channel<Unit>(0)
        val registry = pausedRegistry(pauseChannel, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()
        assertIs<PendingOutboundBannerState.BusyPeer>(component.pendingBanner.value)

        registry.engineFor(peerId).onCancel()
        runCurrent()
        assertIs<PeerTransferState.Cancelled>(registry.engineFor(peerId).state.value)
        runCurrent()
        runCurrent()

        assertIs<PendingOutboundBannerState.TerminalDisplay>(component.pendingBanner.value)

        registry.engineFor(peerId).onDismiss()
        runCurrent()
        runCurrent()
        runCurrent()

        assertIs<PendingOutboundBannerState.Default>(component.pendingBanner.value)
    }

    @Test
    fun `TerminalDisplay shown when outbound transfer completes as Sent`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val registry = fakePeerTransferEngineRegistry(backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("sent.txt", 50L)))
        runCurrent()
        runCurrent()
        runCurrent()
        assertIs<PeerTransferState.Sent>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        assertIs<PendingOutboundBannerState.TerminalDisplay>(component.pendingBanner.value)
    }

    @Test
    fun `TerminalDisplay shown when inbound transfer completes as Received`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = fakePeerTransferEngineRegistry(backgroundScope, inboundEvents)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId)
        runCurrent()
        inboundEvents.emit(ReceiveEvent.Started(currentFile = "remote.txt", totalFiles = 1))
        inboundEvents.emit(ReceiveEvent.BatchCompleted(received = 1, total = 1))
        runCurrent()
        assertIs<PeerTransferState.Received>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        assertIs<PendingOutboundBannerState.TerminalDisplay>(component.pendingBanner.value)
    }

    @Test
    fun `TerminalDisplay shown when inbound transfer fails as Error`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = fakePeerTransferEngineRegistry(backgroundScope, inboundEvents)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId)
        runCurrent()
        inboundEvents.emit(ReceiveEvent.Started(currentFile = "remote.txt", totalFiles = 1))
        inboundEvents.emit(ReceiveEvent.ReceiverSuspended)
        runCurrent()
        assertIs<PeerTransferState.Error>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        assertIs<PendingOutboundBannerState.TerminalDisplay>(component.pendingBanner.value)
    }

    @Test
    fun `peer name falls back to peer id when peer absent from repository`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val pauseChannel = Channel<Unit>(0)
        val registry = pausedRegistry(pauseChannel, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = FakePeersRepository(MutableStateFlow(emptyList())),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(listOf(FakeFileSource("f.txt", 100L)))
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        val state = assertIs<PendingOutboundBannerState.BusyPeer>(component.pendingBanner.value)
        assertEquals(peerId.id, state.peerName)
    }
}
