package com.tubetoast.tether.presentation.banners

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.peer.FakePeersRepository
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PendingFilesSummary
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.fakeBatchSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
        engineRegistry: PeerTransferEngineRegistry = emptyRegistry(),
        conflictRelay: PeerConflictRelay = PeerConflictRelay(),
        coroutineScope: CoroutineScope,
    ): BannersComponent {
        val lifecycle = LifecycleRegistry().also { it.resume() }
        return BannersComponent(
            componentContext = DefaultComponentContext(lifecycle),
            pendingFilesRepository = repo,
            peersRepository = peersRepository,
            engineRegistry = engineRegistry,
            conflictRelay = conflictRelay,
            coroutineScope = coroutineScope,
        )
    }

    private fun fakePeersRepository() = FakePeersRepository(
        MutableStateFlow(listOf(Peer(peerId, Device(peerName, "127.0.0.1", 8080)))),
    )

    private fun emptyRegistry(scope: CoroutineScope? = null): PeerTransferEngineRegistry {
        val registryScope = scope ?: CoroutineScope(kotlinx.coroutines.SupervisorJob())
        return PeerTransferEngineRegistry(
            appScope = registryScope,
            engineFactory = { id, _ ->
                PeerTransferEngine(
                    peer = id,
                    batchSenderFactory = fakeBatchSender(),
                    inboundEvents = MutableSharedFlow(),
                    scope = registryScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )
    }

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

    private fun registryWithInbound(
        inboundEvents: MutableSharedFlow<ReceiveEvent>,
        scope: CoroutineScope,
    ) = PeerTransferEngineRegistry(
        appScope = scope,
        engineFactory = { id, _ ->
            PeerTransferEngine(
                peer = id,
                batchSenderFactory = fakeBatchSender(),
                inboundEvents = inboundEvents,
                scope = scope,
                peerPreferencesStore = FakePeerPreferencesStore(),
            )
        },
    )

    @Test
    fun `onCancelPending clears repo`() = runTest {
        val repo = PendingFilesRepository()
        val component = buildComponent(repo, coroutineScope = backgroundScope)

        repo.setPending(PendingFilesSummary(1, 100L), emptyList())
        component.onCancelPending()

        assertNull(repo.pending.value?.summary)
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

        assertIs<PendingBannerState.Hidden>(component.pendingBanner.value)
    }

    @Test
    fun `pendingBanner is Default when pending files exist and no conflict`() = runTest {
        val repo = PendingFilesRepository()
        val component = buildComponent(repo, coroutineScope = backgroundScope)
        val summary = PendingFilesSummary(2, 1024L)
        repo.setPending(summary, emptyList())

        runCurrent()

        val state = assertIs<PendingBannerState.Default>(component.pendingBanner.value)
        assertEquals(summary, state.summary)
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

        val summary = PendingFilesSummary(1, 100L)
        repo.setPending(summary, listOf(FakeFileSource("share.txt", 100L)))
        runCurrent()

        val state = assertIs<PendingBannerState.Default>(component.pendingBanner.value)
        assertEquals(summary, state.summary)
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
        val summary = PendingFilesSummary(1, 100L)
        repo.setPending(summary, listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        val state = assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)
        assertEquals(summary, state.summary)
        assertEquals(peerName, state.peerName)
        assertEquals(1, state.announcementTick)
    }

    @Test
    fun `BusyPeer shown when engine is ActiveInbound via ReceiveEvent Started`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = registryWithInbound(inboundEvents, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        val summary = PendingFilesSummary(1, 100L)
        repo.setPending(summary, listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId)
        runCurrent()
        inboundEvents.emit(ReceiveEvent.Started(currentFile = "remote.txt", totalFiles = 1))
        runCurrent()
        assertIs<PeerTransferState.ActiveInbound>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        val state = assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)
        assertEquals(summary, state.summary)
        assertEquals(peerName, state.peerName)
    }

    @Test
    fun `BusyPeer shown when engine is Reconnecting via ReceiveEvent ConnectionLost`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = registryWithInbound(inboundEvents, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        val summary = PendingFilesSummary(1, 100L)
        repo.setPending(summary, listOf(FakeFileSource("f.txt", 100L)))

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

        assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)
    }

    @Test
    fun `repeat busy tap on same peer bumps announcementTick`() = runTest {
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
        repo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("f.txt", 100L)))
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()
        val first = assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()
        val second = assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)

        assertEquals(first.announcementTick + 1, second.announcementTick)
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
        repo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("f.txt", 100L)))
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()
        assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)

        repo.clear()
        runCurrent()
        runCurrent()

        assertIs<PendingBannerState.Hidden>(component.pendingBanner.value)
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
        repo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("f.txt", 100L)))
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()
        assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)

        registry.engineFor(peerId).onCancel()
        runCurrent()
        assertIs<PeerTransferState.Cancelled>(registry.engineFor(peerId).state.value)
        runCurrent()
        runCurrent()

        assertIs<PendingBannerState.TerminalDisplay>(component.pendingBanner.value)

        registry.engineFor(peerId).onDismiss()
        runCurrent()
        runCurrent()
        runCurrent()

        assertIs<PendingBannerState.Default>(component.pendingBanner.value)
    }

    @Test
    fun `TerminalDisplay shown when outbound transfer completes as Sent`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val registry = emptyRegistry(backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("f.txt", 100L)))

        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("sent.txt", 50L)))
        runCurrent()
        runCurrent()
        runCurrent()
        assertIs<PeerTransferState.Sent>(registry.engineFor(peerId).state.value)

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        assertIs<PendingBannerState.TerminalDisplay>(component.pendingBanner.value)
    }

    @Test
    fun `TerminalDisplay shown when inbound transfer completes as Received`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = registryWithInbound(inboundEvents, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("f.txt", 100L)))

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

        assertIs<PendingBannerState.TerminalDisplay>(component.pendingBanner.value)
    }

    @Test
    fun `TerminalDisplay shown when inbound transfer fails as Error`() = runTest {
        val repo = PendingFilesRepository()
        val relay = PeerConflictRelay()
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 8)
        val registry = registryWithInbound(inboundEvents, backgroundScope)
        val component = buildComponent(
            repo = repo,
            peersRepository = fakePeersRepository(),
            engineRegistry = registry,
            conflictRelay = relay,
            coroutineScope = backgroundScope,
        )
        repo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("f.txt", 100L)))

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

        assertIs<PendingBannerState.TerminalDisplay>(component.pendingBanner.value)
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
        repo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("f.txt", 100L)))
        registry.engineFor(peerId).startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()

        relay.reportBusyTap(peerId)
        runCurrent()
        runCurrent()
        runCurrent()

        val state = assertIs<PendingBannerState.BusyPeer>(component.pendingBanner.value)
        assertEquals(peerId.id, state.peerName)
    }
}
