package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PendingFilesSummary
import com.tubetoast.tether.transfer.fakeBatchSender
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.serializer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val deviceA = Device(name = "DeviceA", host = "192.168.1.1", port = 8080)
    private val peer = deviceA.toPeerIdentity()

    @Test
    fun `initial stack contains single PeerListChild`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.PeerListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `showTransferDetails pushes TransferDetailsChild then back pops to PeerListChild`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val backDispatcher = BackDispatcher()
        val component = buildComponent(
            backDispatcher = backDispatcher,
            devices = devices,
            coroutineScope = backgroundScope,
        )
        runCurrent()

        component.showTransferDetails(peer)

        assertEquals(2, component.stack.value.items.size)
        assertIs<RootComponent.Child.TransferDetailsChild>(component.stack.value.active.instance)

        backDispatcher.back()

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.PeerListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `childStack initialises to PeerListChild even when supplied StateKeeper bundle is non-empty`() = runTest {
        val priorDispatcher = StateKeeperDispatcher()
        priorDispatcher.register("sentinel", String.serializer()) { "saved" }
        val nonEmptySavedState = priorDispatcher.save()

        val restoredContext = DefaultComponentContext(
            lifecycle = LifecycleRegistry().also { it.resume() },
            stateKeeper = StateKeeperDispatcher(nonEmptySavedState),
        )
        val component = buildComponent(context = restoredContext, coroutineScope = backgroundScope)

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.PeerListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `setPending stores summary and clear resets to null`() = runTest {
        val repo = PendingFilesRepository()

        assertNull(repo.pending.value?.summary)

        val summary = PendingFilesSummary(fileCount = 2, totalBytes = 1024L)
        repo.setPending(summary, emptyList())

        assertEquals(summary, repo.pending.value?.summary)
        assertNotEquals(null, repo.pending.value?.summary)

        repo.clear()

        assertNull(repo.pending.value?.summary)
    }

    @Test
    fun `onCardClick with pending sources routes startOutbound and clears pending`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val repo = PendingFilesRepository()
        val component = buildComponent(
            devices = devices,
            pendingFilesRepository = repo,
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val peerComponent = component.peerListComponent.peerTransferComponent(peer)
        assertNotNull(peerComponent)

        val sources = listOf(FakeFileSource("file.txt", 100L))
        repo.setPending(PendingFilesSummary(1, 100L), sources)
        peerComponent.onCardClick()
        runCurrent()

        assertIs<PeerTransferState.Sent>(peerComponent.state.value.transfer)
        assertNull(repo.pending.value?.summary)
    }

    @Test
    fun `onCardClick without pending sources invokes onOpenPicker`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        var pickerInvoked = false
        val component = buildComponent(
            devices = devices,
            onPickerPick = { pickerInvoked = true },
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val peerComponent = component.peerListComponent.peerTransferComponent(peer)
        assertNotNull(peerComponent)

        peerComponent.onCardClick()

        assertTrue(pickerInvoked)
    }

    @Test
    fun `showTransferDetails pushes TransferDetailsChild onto stack`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(devices = devices, coroutineScope = backgroundScope)
        runCurrent()

        component.showTransferDetails(peer)

        assertEquals(2, component.stack.value.items.size)
        assertIs<RootComponent.Child.TransferDetailsChild>(component.stack.value.active.instance)
    }

    @Test
    fun `transferDetailsComponent onBack pops back to PeerListChild`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val backDispatcher = BackDispatcher()
        val component = buildComponent(
            backDispatcher = backDispatcher,
            devices = devices,
            coroutineScope = backgroundScope,
        )
        runCurrent()

        component.showTransferDetails(peer)
        val detailsChild = component.stack.value.active.instance
        assertIs<RootComponent.Child.TransferDetailsChild>(detailsChild)

        detailsChild.component.onBack()

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.PeerListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `peerTransferComponent onShowDetails pushes TransferDetailsChild`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(devices = devices, coroutineScope = backgroundScope)
        runCurrent()

        val peerComponent = component.peerListComponent.peerTransferComponent(peer)
        assertNotNull(peerComponent)

        peerComponent.onShowDetails()

        assertEquals(2, component.stack.value.items.size)
        val active = component.stack.value.active.instance
        assertIs<RootComponent.Child.TransferDetailsChild>(active)
    }

    private fun buildComponent(
        context: DefaultComponentContext = defaultContext(),
        backDispatcher: BackDispatcher? = null,
        devices: MutableStateFlow<List<Device>> = MutableStateFlow(emptyList()),
        pendingFilesRepository: PendingFilesRepository = PendingFilesRepository(),
        onPickerPick: () -> Unit = {},
        coroutineScope: CoroutineScope,
    ): RootComponent {
        val ctx = if (backDispatcher != null) {
            DefaultComponentContext(
                lifecycle = context.lifecycle,
                stateKeeper = context.stateKeeper,
                instanceKeeper = context.instanceKeeper,
                backHandler = backDispatcher,
            )
        } else {
            context
        }
        val peersRepository = PeersRepository(
            discovery = FakeDeviceDiscovery(devices),
            scope = coroutineScope,
        )
        return RootComponent(
            componentContext = ctx,
            peerListFactory = { childCtx, onShowDetails ->
                PeerListComponent(
                    componentContext = childCtx,
                    peersRepository = peersRepository,
                    peerTransferComponentFactory = { peerCtx, peerLifecycle, peerModel ->
                        val engine = PeerTransferEngine(
                            peer = peerModel.id,
                            batchSenderFactory = fakeBatchSender(),
                            inboundEvents = MutableSharedFlow(),
                            scope = coroutineScope,
                            peerPreferencesStore = FakePeerPreferencesStore(),
                        )
                        PeerTransferComponent(
                            componentContext = peerCtx,
                            peer = peerModel,
                            lifecycleRegistry = peerLifecycle,
                            engine = engine,
                            onShowDetails = onShowDetails,
                            scope = coroutineScope,
                            pendingFilesRepository = pendingFilesRepository,
                            onOpenPicker = onPickerPick,
                            conflictRelay = PeerConflictRelay(),
                        )
                    },
                    bannersComponentFactory = { bannersCtx ->
                        BannersComponent(
                            componentContext = bannersCtx,
                            pendingFilesRepository = pendingFilesRepository,
                            peersRepository = peersRepository,
                            engineRegistry = PeerTransferEngineRegistry(
                                appScope = coroutineScope,
                                engineFactory = { id, engineScope ->
                                    PeerTransferEngine(
                                        peer = id,
                                        batchSenderFactory = fakeBatchSender(),
                                        inboundEvents = MutableSharedFlow(),
                                        scope = engineScope,
                                        peerPreferencesStore = FakePeerPreferencesStore(),
                                    )
                                },
                            ),
                            conflictRelay = PeerConflictRelay(),
                            coroutineScope = coroutineScope,
                        )
                    },
                    coroutineScope = coroutineScope,
                )
            },
        )
    }

    private fun defaultContext(): DefaultComponentContext {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }
}
