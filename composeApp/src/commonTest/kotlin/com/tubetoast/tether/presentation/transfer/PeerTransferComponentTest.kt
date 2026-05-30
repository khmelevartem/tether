package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.presentation.PendingFilesSummary
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.fakeBatchSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
        pendingFilesRepository: PendingFilesRepository? = null,
        onOpenPicker: () -> Unit = {},
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
        )
        val component = PeerTransferComponent(
            componentContext = context,
            peer = peer,
            lifecycleRegistry = lifecycle,
            engine = engine,
            onShowDetails = {},
            scope = scope,
            pendingFilesRepository = pendingFilesRepository,
            onOpenPicker = onOpenPicker,
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
        assertNull(repo.summary.value)
    }

    @Test
    fun `onCardClick without pending sources invokes onOpenPicker`() = runTest {
        var pickerInvoked = false
        val (component) = buildComponent(
            onOpenPicker = { pickerInvoked = true },
            scope = backgroundScope,
        )

        component.onCardClick()

        assertTrue(pickerInvoked)
    }

    @Test
    fun `destroyContext destroys the lifecycle`() = runTest {
        val (component, lifecycle) = buildComponent(scope = backgroundScope)
        component.destroyContext()
        assertEquals(Lifecycle.State.DESTROYED, lifecycle.state)
    }
}
