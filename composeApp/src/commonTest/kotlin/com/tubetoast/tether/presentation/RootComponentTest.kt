package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.TransferDetailsComponent
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.FakeConnectionMonitor
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {
    @Test
    fun `initial stack contains single DeviceListChild`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        assertEquals(1, component.stack.value.items.size)
        assertIs<RootComponent.Child.DeviceListChild>(component.stack.value.active.instance)
    }

    @Test
    fun `showTransferDetails pushes TransferDetailsChild then back pops to DeviceListChild`() = runTest {
        val peer = PeerIdentity("peer-1")
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
    fun `setPendingFiles stores summary and clearPendingFiles restores NONE`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        assertSame(PendingFilesSummary.NONE, component.pendingFiles.value)

        val summary = PendingFilesSummary()
        component.setPendingFiles(summary, emptyList())

        assertSame(summary, component.pendingFiles.value)
        assertNotSame(PendingFilesSummary.NONE, component.pendingFiles.value)

        component.clearPendingFiles()

        assertSame(PendingFilesSummary.NONE, component.pendingFiles.value)
    }

    private fun buildComponent(
        context: DefaultComponentContext = defaultContext(),
        backDispatcher: BackDispatcher? = null,
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
        return RootComponent(
            componentContext = ctx,
            deviceListFactory = { childCtx ->
                DeviceListComponent(
                    componentContext = childCtx,
                    discovery = FakeDeviceDiscovery(),
                    coroutineScope = coroutineScope,
                )
            },
            transferDetailsFactory = { childCtx, peer ->
                val monitor = FakeConnectionMonitor()
                val peerComponent = PeerTransferComponent(
                    componentContext = childCtx,
                    peer = peer,
                    batchSenderFactory = {
                        BatchSender(
                            sendOne = { _, _ -> },
                            connectionMonitor = monitor,
                            progressThrottle = 100.milliseconds,
                            dispatcher = Dispatchers.Unconfined,
                        )
                    },
                    inboundEvents = MutableSharedFlow(),
                    onShowDetailsCallback = {},
                    scope = coroutineScope,
                )
                TransferDetailsComponent(
                    componentContext = childCtx,
                    peerComponent = peerComponent,
                    onBack = {},
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
