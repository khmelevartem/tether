package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.preferences.FakeFileTransferPreferences
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFilePicker
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PendingFilesSummary
import com.tubetoast.tether.transfer.PickKind
import com.tubetoast.tether.transfer.fakeBatchSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferComponentTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val peer = Peer(
        id = PeerIdentity("test-peer"),
        device = Device(name = "TestDevice", host = "127.0.0.1", port = 8080),
    )

    private fun buildComponent(
        pendingFilesRepository: PendingFilesRepository = PendingFilesRepository(),
        filePicker: FakeFilePicker = FakeFilePicker(result = emptyList()),
        conflictRelay: PeerConflictRelay = PeerConflictRelay(),
        scope: kotlinx.coroutines.CoroutineScope,
    ): Pair<PeerTransferComponent, LifecycleRegistry> {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle)
        val engine = PeerTransferEngine(
            peer = peer.id,
            batchSenderFactory = fakeBatchSender(),
            inboundEvents = MutableSharedFlow(),
            scope = scope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )
        val component = PeerTransferComponent(
            componentContext = context,
            peer = peer,
            lifecycleRegistry = lifecycle,
            engine = engine,
            onShowDetails = {},
            scope = scope,
            pendingFilesRepository = pendingFilesRepository,
            filePicker = filePicker,
            conflictRelay = conflictRelay,
            fileTransferPreferences = FakeFileTransferPreferences(),
        )
        return component to lifecycle
    }

    @Test
    fun `state Value mirrors engine StateFlow`() = runTest {
        val (component) = buildComponent(scope = backgroundScope)

        assertIs<PeerTransferState.Idle>(component.state.value.transfer)

        component.startOutbound(listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()

        assertIs<PeerTransferState.Sent>(component.state.value.transfer)
    }

    @Test
    fun `toggleExpanded flips expanded and preserves transfer state`() = runTest {
        val (component) = buildComponent(scope = backgroundScope)

        assertIs<PeerTransferState.Idle>(component.state.value.transfer)
        assertEquals(false, component.state.value.expanded)

        component.toggleExpanded()
        runCurrent()

        assertTrue(component.state.value.expanded)
        assertIs<PeerTransferState.Idle>(component.state.value.transfer)

        component.toggleExpanded()
        runCurrent()

        assertEquals(false, component.state.value.expanded)
    }

    @Test
    fun `onCardClick with pending sources starts outbound and clears repo`() = runTest {
        val repo = PendingFilesRepository()
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            scope = backgroundScope,
        )

        val sources = listOf(FakeFileSource("file.txt", 100L))
        repo.setPending(PendingFilesSummary(1, 100L), sources)

        component.onCardClick()
        runCurrent()

        assertIs<PeerTransferState.Sent>(component.state.value.transfer)
        assertNull(repo.pending.value?.summary)
    }

    @Test
    fun `onCardClick without pending sources invokes picker with Files kind`() = runTest {
        val repo = PendingFilesRepository()
        val picker = FakeFilePicker(result = emptyList())
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            filePicker = picker,
            scope = backgroundScope,
        )

        component.onCardClick()
        runCurrent()

        assertTrue(picker.pickFilesCalled)
    }

    @Test
    fun `onCardClick when engine busy preserves pending and reports to conflictRelay`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val relay = PeerConflictRelay()
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val engine = PeerTransferEngine(
            peer = peer.id,
            batchSenderFactory = fakeBatchSender(pauseChannel = pauseChannel),
            inboundEvents = MutableSharedFlow(),
            scope = backgroundScope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )
        val repo = PendingFilesRepository()
        val component = PeerTransferComponent(
            componentContext = DefaultComponentContext(lifecycle),
            peer = peer,
            lifecycleRegistry = lifecycle,
            engine = engine,
            onShowDetails = {},
            scope = backgroundScope,
            pendingFilesRepository = repo,
            filePicker = FakeFilePicker(result = emptyList()),
            conflictRelay = relay,
            fileTransferPreferences = FakeFileTransferPreferences(),
        )

        engine.startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(component.state.value.transfer)

        val shareSource = listOf(FakeFileSource("share.txt", 100L))
        repo.setPending(PendingFilesSummary(1, 100L), shareSource)

        val emitted = mutableListOf<PeerIdentity>()
        val collectJob = backgroundScope.launch { relay.busyTaps.collect { emitted.add(it) } }
        runCurrent()

        component.onCardClick()
        runCurrent()

        assertIs<PeerTransferState.ActiveOutbound>(component.state.value.transfer)
        assertTrue(repo.pending.value != null, "pending must survive busy tap")
        assertEquals(listOf(peer.id), emitted, "conflictRelay must receive the busy peer identity")

        collectJob.cancel()
    }

    @Test
    fun `onPick with non-empty result starts outbound directly without setPending`() = runTest {
        val repo = PendingFilesRepository()
        val sources = listOf(FakeFileSource("photo.jpg", 200L))
        val picker = FakeFilePicker(result = sources)
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            filePicker = picker,
            scope = backgroundScope,
        )

        component.onPick(PickKind.Files)
        runCurrent()

        assertIs<PeerTransferState.Sent>(component.state.value.transfer)
        assertNull(repo.pending.value, "onPick must NOT call setPending — goes directly to engine")
    }

    @Test
    fun `onPick with empty result does not start outbound`() = runTest {
        val repo = PendingFilesRepository()
        val picker = FakeFilePicker(result = emptyList())
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            filePicker = picker,
            scope = backgroundScope,
        )

        component.onPick(PickKind.Files)
        runCurrent()

        assertIs<PeerTransferState.Idle>(component.state.value.transfer)
        assertNull(repo.pending.value)
    }

    @Test
    fun `onPick Photos delegates to pickPhotos`() = runTest {
        val sources = listOf(FakeFileSource("img.png", 50L))
        val picker = FakeFilePicker(result = sources)
        val repo = PendingFilesRepository()
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            filePicker = picker,
            scope = backgroundScope,
        )

        component.onPick(PickKind.Photos)
        runCurrent()

        assertTrue(picker.pickPhotosCalled)
        assertIs<PeerTransferState.Sent>(component.state.value.transfer)
    }

    @Test
    fun `onPick Folder delegates to pickFolder`() = runTest {
        val sources = listOf(FakeFileSource("doc.pdf", 100L))
        val picker = FakeFilePicker(result = sources)
        val repo = PendingFilesRepository()
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            filePicker = picker,
            scope = backgroundScope,
        )

        component.onPick(PickKind.Folder)
        runCurrent()

        assertTrue(picker.pickFolderCalled)
        assertIs<PeerTransferState.Sent>(component.state.value.transfer)
    }

    @Test
    fun `onPick when engine busy does not set pending and reports to conflictRelay`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val relay = PeerConflictRelay()
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val engine = PeerTransferEngine(
            peer = peer.id,
            batchSenderFactory = fakeBatchSender(pauseChannel = pauseChannel),
            inboundEvents = MutableSharedFlow(),
            scope = backgroundScope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )
        val repo = PendingFilesRepository()
        val sources = listOf(FakeFileSource("picked.txt", 100L))
        val picker = FakeFilePicker(result = sources)
        val component = PeerTransferComponent(
            componentContext = DefaultComponentContext(lifecycle),
            peer = peer,
            lifecycleRegistry = lifecycle,
            engine = engine,
            onShowDetails = {},
            scope = backgroundScope,
            pendingFilesRepository = repo,
            filePicker = picker,
            conflictRelay = relay,
            fileTransferPreferences = FakeFileTransferPreferences(),
        )

        engine.startOutbound(listOf(FakeFileSource("in-flight.txt", 50L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(component.state.value.transfer)

        val emitted = mutableListOf<PeerIdentity>()
        val collectJob = backgroundScope.launch { relay.busyTaps.collect { emitted.add(it) } }
        runCurrent()

        component.onPick(PickKind.Files)
        runCurrent()

        assertIs<PeerTransferState.ActiveOutbound>(component.state.value.transfer)
        assertNull(repo.pending.value, "onPick must NOT set pending")
        assertEquals(listOf(peer.id), emitted)

        collectJob.cancel()
    }

    @Test
    fun `large selection gates both onPick and onCardClick behind confirm`() = runTest {
        val repo = PendingFilesRepository()
        // 501 files exceeds the 500-file threshold
        val bigSources = (1..501).map { FakeFileSource("f$it.txt", 100L) }
        val picker = FakeFilePicker(result = bigSources)
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            filePicker = picker,
            scope = backgroundScope,
        )

        component.onPick(PickKind.Files)
        runCurrent()

        assertIs<PeerTransferState.Idle>(component.state.value.transfer)
        assertNotNull(component.state.value.largeConfirm, "large-confirm dialog must be pending")

        component.onConfirmLargeSelection(dontShowAgain = false)
        runCurrent()

        assertIs<PeerTransferState.Sent>(component.state.value.transfer)
        assertNull(component.state.value.largeConfirm)
    }

    @Test
    fun `confirm with dontShowAgain suppresses the warning preference and sends`() = runTest {
        val repo = PendingFilesRepository()
        val bigSources = (1..501).map { FakeFileSource("f$it.txt", 100L) }
        val picker = FakeFilePicker(result = bigSources)
        val prefs = FakeFileTransferPreferences()
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val engine = PeerTransferEngine(
            peer = peer.id,
            batchSenderFactory = fakeBatchSender(),
            inboundEvents = MutableSharedFlow(),
            scope = backgroundScope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )
        val component = PeerTransferComponent(
            componentContext = DefaultComponentContext(lifecycle),
            peer = peer,
            lifecycleRegistry = lifecycle,
            engine = engine,
            onShowDetails = {},
            scope = backgroundScope,
            pendingFilesRepository = repo,
            filePicker = picker,
            conflictRelay = PeerConflictRelay(),
            fileTransferPreferences = prefs,
        )

        component.onPick(PickKind.Files)
        runCurrent()
        assertNotNull(component.state.value.largeConfirm)

        component.onConfirmLargeSelection(dontShowAgain = true)
        runCurrent()

        assertEquals(
            false,
            prefs.observeLargeSelectionWarning().first(),
            "dontShowAgain must suppress the large-selection warning",
        )
        assertIs<PeerTransferState.Sent>(component.state.value.transfer)
        assertNull(component.state.value.largeConfirm)
    }

    @Test
    fun `onDismissLargeSelection clears confirm without starting outbound`() = runTest {
        val repo = PendingFilesRepository()
        val bigSources = (1..501).map { FakeFileSource("f$it.txt", 100L) }
        val picker = FakeFilePicker(result = bigSources)
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            filePicker = picker,
            scope = backgroundScope,
        )

        component.onPick(PickKind.Files)
        runCurrent()

        assertNotNull(component.state.value.largeConfirm)

        component.onDismissLargeSelection()
        runCurrent()

        assertNull(component.state.value.largeConfirm)
        assertIs<PeerTransferState.Idle>(component.state.value.transfer)
    }

    @Test
    fun `large selection confirm also fires for onCardClick with pending sources`() = runTest {
        val repo = PendingFilesRepository()
        val bigSources = (1..501).map { FakeFileSource("f$it.txt", 100L) }
        repo.setPending(PendingFilesSummary(bigSources.size, bigSources.sumOf { it.sizeBytes ?: 0L }), bigSources)
        val (component) = buildComponent(
            pendingFilesRepository = repo,
            scope = backgroundScope,
        )

        component.onCardClick()
        runCurrent()

        assertIs<PeerTransferState.Idle>(component.state.value.transfer)
        assertNotNull(component.state.value.largeConfirm, "large-confirm must fire for onCardClick too")
    }

    @Test
    fun `concurrent double onPick invokes picker only once while first is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val picker = FakeFilePicker(result = listOf(FakeFileSource("a.txt", 100L)), gate = gate)
        val (component) = buildComponent(filePicker = picker, scope = backgroundScope)

        component.onPick(PickKind.Files)
        component.onPick(PickKind.Files)
        runCurrent()

        assertEquals(1, picker.pickFilesCallCount, "second onPick while first is in flight must be ignored")

        gate.complete(Unit)
        runCurrent()

        assertIs<PeerTransferState.Sent>(component.state.value.transfer)

        component.onPick(PickKind.Files)
        runCurrent()

        assertEquals(2, picker.pickFilesCallCount, "onPick must work again after flag is reset")
    }

    @Test
    fun `destroyContext destroys the lifecycle`() = runTest {
        val (component, lifecycle) = buildComponent(scope = backgroundScope)
        component.destroyContext()
        assertEquals(Lifecycle.State.DESTROYED, lifecycle.state)
    }

    @Test
    fun `engine survives destroyContext and re-attaches on new component`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val registry = PeerTransferEngineRegistry(
            appScope = backgroundScope,
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

        val lifecycleA = LifecycleRegistry()
        lifecycleA.resume()
        val componentA = PeerTransferComponent(
            componentContext = DefaultComponentContext(lifecycleA),
            peer = peer,
            lifecycleRegistry = lifecycleA,
            engine = registry.engineFor(peer.id),
            onShowDetails = {},
            scope = backgroundScope,
            pendingFilesRepository = PendingFilesRepository(),
            filePicker = FakeFilePicker(result = emptyList()),
            conflictRelay = PeerConflictRelay(),
            fileTransferPreferences = FakeFileTransferPreferences(),
        )

        componentA.startOutbound(listOf(FakeFileSource("file.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(componentA.state.value.transfer)

        componentA.destroyContext()

        val lifecycleB = LifecycleRegistry()
        lifecycleB.resume()
        val componentB = PeerTransferComponent(
            componentContext = DefaultComponentContext(lifecycleB),
            peer = peer,
            lifecycleRegistry = lifecycleB,
            engine = registry.engineFor(peer.id),
            onShowDetails = {},
            scope = backgroundScope,
            pendingFilesRepository = PendingFilesRepository(),
            filePicker = FakeFilePicker(result = emptyList()),
            conflictRelay = PeerConflictRelay(),
            fileTransferPreferences = FakeFileTransferPreferences(),
        )
        runCurrent()

        assertIs<PeerTransferState.ActiveOutbound>(componentB.state.value.transfer)
    }
}
