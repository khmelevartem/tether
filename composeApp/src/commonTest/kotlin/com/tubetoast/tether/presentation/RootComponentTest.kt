package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.presentation.transfer.TransferRegistry
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.FakeConnectionMonitor
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {
    private val deviceA = Device(name = "DeviceA", host = "192.168.1.1", port = 8080)
    private val peer = deviceA.toPeerIdentity()

    @Test
    fun `initial stack contains single DeviceListChild`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.DeviceListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `showTransferDetails pushes TransferDetailsChild then back pops to DeviceListChild`() = runTest {
        val backDispatcher = BackDispatcher()
        val component = buildComponent(
            backDispatcher = backDispatcher,
            coroutineScope = backgroundScope,
        )

        component.showTransferDetails(peer)

        assertEquals(2, component.stack.value.items.size)
        assertIs<RootComponent.Child.TransferDetailsChild>(component.stack.value.active.instance)

        backDispatcher.back()

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.DeviceListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `childStack initialises to DeviceListChild even when supplied StateKeeper bundle is non-empty`() = runTest {
        // serializer = null means childStack writes nothing to StateKeeper and reads nothing back.
        // This pins the contract: a non-empty bundle from a prior process is ignored — even one
        // carrying entries under the very key Decompose would use if a real serializer were wired.
        val priorDispatcher = StateKeeperDispatcher()
        priorDispatcher.register("sentinel", String.serializer()) { "saved" }
        val nonEmptySavedState = priorDispatcher.save()

        val restoredContext = DefaultComponentContext(
            lifecycle = LifecycleRegistry().also { it.resume() },
            stateKeeper = StateKeeperDispatcher(nonEmptySavedState),
        )
        val component = buildComponent(context = restoredContext, coroutineScope = backgroundScope)

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.DeviceListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `setPendingFiles stores summary and clearPendingFiles resets to NONE`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        assertSame(PendingFilesSummary.NONE, component.pendingFiles.value)

        val summary = PendingFilesSummary(fileCount = 2, totalBytes = 1024L)
        component.setPendingFiles(summary, emptyList())

        assertEquals(summary, component.pendingFiles.value)
        assertNotEquals(PendingFilesSummary.NONE, component.pendingFiles.value)

        component.clearPendingFiles()

        assertSame(PendingFilesSummary.NONE, component.pendingFiles.value)
    }

    @Test
    fun `onPeerTapped with pending sources routes startOutbound and clears pending`() = runTest {
        val ctx = defaultContext()
        val component = buildComponent(
            context = ctx,
            registryFactory = buildRegistryFactory(ctx, backgroundScope, MutableStateFlow(listOf(deviceA))),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val sources = listOf(FakeFileSource("file.txt", 100L))
        component.setPendingFiles(PendingFilesSummary(1, 100L), sources)
        component.onPeerTapped(peer)
        runCurrent()

        assertIs<PeerTransferState.Sent>(
            component.registry
                .get(peer)
                .state.value,
        )
        assertSame(PendingFilesSummary.NONE, component.pendingFiles.value)
    }

    @Test
    fun `showTransferDetails pushes TransferDetailsChild onto stack`() = runTest {
        val ctx = defaultContext()
        val component = buildComponent(
            context = ctx,
            registryFactory = buildRegistryFactory(ctx, backgroundScope, MutableStateFlow(listOf(deviceA))),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        component.showTransferDetails(peer)

        assertEquals(2, component.stack.value.items.size)
        assertIs<RootComponent.Child.TransferDetailsChild>(component.stack.value.active.instance)
    }

    @Test
    fun `transferDetailsComponent onBack pops back to DeviceListChild`() = runTest {
        val ctx = defaultContext()
        val backDispatcher = BackDispatcher()
        val component = buildComponent(
            context = ctx,
            backDispatcher = backDispatcher,
            registryFactory = buildRegistryFactory(ctx, backgroundScope, MutableStateFlow(listOf(deviceA))),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        component.showTransferDetails(peer)
        val detailsChild = component.stack.value.active.instance
        assertIs<RootComponent.Child.TransferDetailsChild>(detailsChild)

        detailsChild.component.onBack()

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.DeviceListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `onPeerTapped without pending sources is a no-op`() = runTest {
        val ctx = defaultContext()
        val component = buildComponent(
            context = ctx,
            registryFactory = buildRegistryFactory(ctx, backgroundScope, MutableStateFlow(listOf(deviceA))),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        component.onPeerTapped(peer)

        assertIs<PeerTransferState.Idle>(
            component.registry
                .get(peer)
                .state.value,
        )
    }

    private fun buildRegistryFactory(
        ctx: DefaultComponentContext,
        coroutineScope: CoroutineScope,
        devices: MutableStateFlow<List<Device>> = MutableStateFlow(emptyList()),
    ): (onShowDetails: (PeerIdentity) -> Unit) -> TransferRegistry = { onShowDetails ->
        TransferRegistry(
            componentContext = ctx,
            peerComponentFactory = { childCtx, peer ->
                PeerTransferComponent(
                    componentContext = childCtx,
                    peer = peer,
                    batchSenderFactory = {
                        BatchSender(
                            sendOne = { src, onProgress ->
                                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                            },
                            connectionMonitor = FakeConnectionMonitor(),
                            progressThrottle = 100.milliseconds,
                            dispatcher = Dispatchers.Unconfined,
                        )
                    },
                    inboundEvents = MutableSharedFlow(),
                    onShowDetailsCallback = onShowDetails,
                    scope = coroutineScope,
                )
            },
            discoveredDevices = devices,
            scope = coroutineScope,
            mainDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun buildComponent(
        context: DefaultComponentContext = defaultContext(),
        backDispatcher: BackDispatcher? = null,
        coroutineScope: CoroutineScope,
        registryFactory: ((PeerIdentity) -> Unit) -> TransferRegistry = buildRegistryFactory(context, coroutineScope),
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
            discovery = FakeDeviceDiscovery(),
            scope = coroutineScope,
        )
        return RootComponent(
            componentContext = ctx,
            deviceListFactory = { childCtx, registry ->
                PeerListComponent(
                    componentContext = childCtx,
                    peersRepository = peersRepository,
                    peerTransferComponentFactory = { _, peer -> registry.get(peer.id) },
                    coroutineScope = coroutineScope,
                )
            },
            registryFactory = registryFactory,
            coroutineScope = coroutineScope,
        )
    }

    private fun defaultContext(): DefaultComponentContext {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }
}
