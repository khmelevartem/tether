package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.presentation.transfer.PendingFilesRepository
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.fakeBatchSender
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {
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

        assertNull(repo.summary.value)

        val summary = PendingFilesSummary(fileCount = 2, totalBytes = 1024L)
        repo.setPending(summary, emptyList())

        assertEquals(summary, repo.summary.value)
        assertNotEquals(null, repo.summary.value)

        repo.clear()

        assertNull(repo.summary.value)
    }

    @Test
    fun `onPeerTapped with pending sources routes startOutbound and clears pending`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val repo = PendingFilesRepository()
        val component = buildComponent(
            devices = devices,
            pendingFilesRepository = repo,
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val peerList = component.peerListComponent
        val peerComponent = peerList.peerTransferComponent(peer)
        assertNotNull(peerComponent)

        val sources = listOf(FakeFileSource("file.txt", 100L))
        repo.setPending(PendingFilesSummary(1, 100L), sources)
        peerList.onPeerTapped(peer)
        runCurrent()

        assertIs<PeerTransferState.Sent>(peerComponent.state.value)
        assertNull(repo.summary.value)
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

    @Test
    fun `onPeerTapped without pending sources is a no-op`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(devices = devices, coroutineScope = backgroundScope)
        runCurrent()

        val peerList = component.peerListComponent
        val peerComponent = peerList.peerTransferComponent(peer)
        assertNotNull(peerComponent)

        peerList.onPeerTapped(peer)

        assertIs<PeerTransferState.Idle>(peerComponent.state.value)
    }

    private fun buildComponent(
        context: DefaultComponentContext = defaultContext(),
        backDispatcher: BackDispatcher? = null,
        devices: MutableStateFlow<List<Device>> = MutableStateFlow(emptyList()),
        pendingFilesRepository: PendingFilesRepository = PendingFilesRepository(),
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
            bannersFactory = { childCtx ->
                BannersComponent(
                    componentContext = childCtx,
                    pendingFilesRepository = pendingFilesRepository,
                    coroutineScope = coroutineScope,
                )
            },
            peerListFactory = { childCtx, onShowDetails ->
                PeerListComponent(
                    componentContext = childCtx,
                    peersRepository = peersRepository,
                    peerTransferComponentFactory = { peerCtx, peer ->
                        PeerTransferComponent(
                            componentContext = peerCtx,
                            peer = peer.id,
                            batchSenderFactory = fakeBatchSender(),
                            inboundEvents = MutableSharedFlow(),
                            onShowDetails = onShowDetails,
                            scope = coroutineScope,
                        )
                    },
                    pendingFilesRepository = pendingFilesRepository,
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
