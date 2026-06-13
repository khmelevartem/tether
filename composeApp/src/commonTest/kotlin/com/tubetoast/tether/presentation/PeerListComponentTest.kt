package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.peer.FakePeersRepository
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFilePicker
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PickKind
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val PeerTransferComponent.peerState get() = state.value

@OptIn(ExperimentalCoroutinesApi::class)
class PeerListComponentTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val deviceA = Device(name = "DeviceA", host = "192.168.1.1", port = 8080)
    private val deviceB = Device(name = "DeviceB", host = "192.168.1.2", port = 8080)

    @Test
    fun `empty state when no devices discovered`() = runTest {
        val component = buildComponent(emptyList(), coroutineScope = backgroundScope)
        assertEquals(emptyList(), component.state.value.rows)
    }

    @Test
    fun `rows contain discovered devices`() = runTest {
        val flow = MutableStateFlow<List<Device>>(emptyList())
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)

        flow.value = listOf(deviceA, deviceB)
        runCurrent()

        assertEquals(2, component.state.value.rows.size)
        assertEquals(
            setOf(deviceA, deviceB),
            component.state.value.rows
                .map { it.peerState.device }
                .toSet(),
        )
    }

    @Test
    fun `rows update when device disappears`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA, deviceB))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()
        assertEquals(2, component.state.value.rows.size)

        flow.value = listOf(deviceA)
        runCurrent()

        assertEquals(1, component.state.value.rows.size)
        assertEquals(
            deviceA,
            component.state.value.rows
                .first()
                .peerState.device,
        )
    }

    @Test
    fun `offline peer with active transfer is not in rows when absent from emit`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()

        val peerComponent = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(peerComponent)
        peerComponent.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()

        flow.value = emptyList()
        runCurrent()

        assertNull(
            component.state.value.rows
                .firstOrNull { it.peerId == deviceA.toPeerIdentity() },
        )
        assertNull(component.peerTransferComponent(deviceA.toPeerIdentity()))
    }

    @Test
    fun `offline idle peer absent from emit is evicted`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()

        flow.value = emptyList()
        runCurrent()

        assertNull(
            component.state.value.rows
                .firstOrNull { it.peerId == deviceA.toPeerIdentity() },
        )
        assertNull(component.peerTransferComponent(deviceA.toPeerIdentity()))
    }

    @Test
    fun `destroyContext called when peer leaves emit`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA, deviceB))
        val destroyedIds = mutableListOf<String>()
        val component = buildComponent(
            flow = flow,
            coroutineScope = backgroundScope,
            onDestroyContext = { peerId -> destroyedIds += peerId },
        )
        runCurrent()

        flow.value = listOf(deviceA)
        runCurrent()

        assertEquals(listOf(deviceB.toPeerIdentity().id), destroyedIds)
    }

    @Test
    fun `peer evicted after dismissing terminal state while offline`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()

        val peerComponent = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(peerComponent)
        peerComponent.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()

        flow.value = emptyList()
        runCurrent()
        assertNull(component.peerTransferComponent(deviceA.toPeerIdentity()))

        peerComponent.onDismiss()
        runCurrent()

        flow.value = listOf(deviceA)
        runCurrent()

        assertNotNull(component.peerTransferComponent(deviceA.toPeerIdentity()))
        assertNotNull(
            component.state.value.rows
                .firstOrNull { it.peerId == deviceA.toPeerIdentity() },
        )
    }

    @Test
    fun `peer returns in next emit and row reappears with fresh component`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()

        val first = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(first)
        first.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()

        flow.value = emptyList()
        runCurrent()

        flow.value = listOf(deviceA)
        runCurrent()

        assertNotNull(
            component.state.value.rows
                .firstOrNull { it.peerId == deviceA.toPeerIdentity() },
        )
        val second = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(second)
        assertNotEquals(first, second)
    }

    @Test
    fun `row carries live transfer component`() = runTest {
        val flow = MutableStateFlow<List<Device>>(emptyList())
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)

        flow.value = listOf(deviceA)
        runCurrent()

        assertIs<PeerTransferState.Idle>(
            component.state.value.rows
                .first()
                .state.value.transfer,
        )

        val peerComponent = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(peerComponent)
        peerComponent.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()

        assertIs<PeerTransferState.Sent>(
            component.state.value.rows
                .first()
                .state.value.transfer,
        )
    }

    @Test
    fun `peerTransferComponent returns stable instance`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()

        val first = component.peerTransferComponent(deviceA.toPeerIdentity())
        val second = component.peerTransferComponent(deviceA.toPeerIdentity())

        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun `isOnline flag comes from repository as-is`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()

        assertEquals(
            true,
            component.state.value.rows
                .first { it.peerId == deviceA.toPeerIdentity() }
                .peerState.isOnline,
        )
    }

    @Test
    fun `offline peer in emit has isOnline false in row`() = runTest {
        val peerA = Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = false)
        val peerFlow = MutableStateFlow(listOf(peerA))
        val component = buildComponentWithPeers(peerFlow = peerFlow, coroutineScope = backgroundScope)
        runCurrent()

        assertEquals(1, component.state.value.rows.size)
        assertEquals(
            false,
            component.state.value.rows
                .first()
                .peerState.isOnline,
        )
    }

    @Test
    fun `offline idle peer in emit retains component and row`() = runTest {
        val peerOnline = Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = true)
        val peerFlow = MutableStateFlow(listOf(peerOnline))
        val component = buildComponentWithPeers(peerFlow = peerFlow, coroutineScope = backgroundScope)
        runCurrent()

        assertNotNull(component.peerTransferComponent(deviceA.toPeerIdentity()))

        val peerOfflineIdle = peerOnline.copy(isOnline = false)
        peerFlow.value = listOf(peerOfflineIdle)
        runCurrent()

        assertNotNull(component.peerTransferComponent(deviceA.toPeerIdentity()))
        assertEquals(1, component.state.value.rows.size)
        assertEquals(
            false,
            component.state.value.rows
                .first()
                .peerState.isOnline,
        )
    }

    @Test
    fun `offline non-idle peer in emit retains component and row`() = runTest {
        val peerOnline = Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = true)
        val peerFlow = MutableStateFlow(listOf(peerOnline))
        val component = buildComponentWithPeers(peerFlow = peerFlow, coroutineScope = backgroundScope)
        runCurrent()

        val peerComponent = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(peerComponent)
        peerComponent.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()

        val peerOffline = peerOnline.copy(isOnline = false)
        peerFlow.value = listOf(peerOffline)
        runCurrent()

        assertNotNull(component.peerTransferComponent(deviceA.toPeerIdentity()))
        assertEquals(1, component.state.value.rows.size)
        assertEquals(
            false,
            component.state.value.rows
                .first()
                .peerState.isOnline,
        )
    }

    private fun buildComponent(
        initial: List<Device> = emptyList(),
        flow: MutableStateFlow<List<Device>> = MutableStateFlow(initial),
        coroutineScope: CoroutineScope,
        onDestroyContext: (String) -> Unit = {},
        isPickerModeChooserNeeded: Boolean = false,
        filePicker: FakeFilePicker = FakeFilePicker(result = emptyList()),
    ): PeerListComponent {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle)
        lifecycle.resume()
        return PeerListComponent(
            componentContext = context,
            peersRepository = PeersRepository(
                discovery = FakeDeviceDiscovery(flow),
                scope = coroutineScope,
            ),
            peerTransferComponentFactory = fakePeerTransferComponentFactory(
                coroutineScope = coroutineScope,
                onDestroyContext = onDestroyContext,
                filePicker = filePicker,
            ),
            bannersComponentFactory = fakeBannersComponentFactory(coroutineScope),
            deviceNameComponentFactory = fakeDeviceNameComponentFactory(coroutineScope),
            isPickerModeChooserNeeded = isPickerModeChooserNeeded,
            coroutineScope = coroutineScope,
        )
    }

    private fun buildComponentWithPeers(
        peerFlow: MutableStateFlow<List<Peer>>,
        coroutineScope: CoroutineScope,
        onDestroyContext: (String) -> Unit = {},
        isPickerModeChooserNeeded: Boolean = false,
    ): PeerListComponent {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle)
        lifecycle.resume()
        return PeerListComponent(
            componentContext = context,
            peersRepository = FakePeersRepository(peerFlow),
            peerTransferComponentFactory = fakePeerTransferComponentFactory(coroutineScope, onDestroyContext),
            bannersComponentFactory = fakeBannersComponentFactory(coroutineScope),
            deviceNameComponentFactory = fakeDeviceNameComponentFactory(coroutineScope),
            isPickerModeChooserNeeded = isPickerModeChooserNeeded,
            coroutineScope = coroutineScope,
        )
    }

    @Test
    fun `mixed online and offline peers in same emit produce mixed-flag rows`() = runTest {
        val peerOnline = Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = true)
        val peerOffline = Peer(id = deviceB.toPeerIdentity(), device = deviceB, isOnline = false)
        val peerFlow = MutableStateFlow(listOf(peerOnline, peerOffline))
        val component = buildComponentWithPeers(peerFlow = peerFlow, coroutineScope = backgroundScope)
        runCurrent()

        val rows = component.state.value.rows
        assertEquals(2, rows.size)
        assertEquals(true, rows.first { it.peerId == deviceA.toPeerIdentity() }.peerState.isOnline)
        assertEquals(false, rows.first { it.peerId == deviceB.toPeerIdentity() }.peerState.isOnline)
    }

    @Test
    fun `destroyContext is not called when peer flips to offline in emit`() = runTest {
        val peerOnline = Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = true)
        val peerFlow = MutableStateFlow(listOf(peerOnline))
        val destroyedIds = mutableListOf<String>()
        val component = buildComponentWithPeers(
            peerFlow = peerFlow,
            coroutineScope = backgroundScope,
            onDestroyContext = { destroyedIds += it },
        )
        runCurrent()

        peerFlow.value = listOf(peerOnline.copy(isOnline = false))
        runCurrent()

        assertEquals(emptyList(), destroyedIds)
        assertNotNull(component.peerTransferComponent(deviceA.toPeerIdentity()))
    }

    @Test
    fun `peer flipping to offline-in-emit retains same transferComponent instance`() = runTest {
        val peerOnline = Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = true)
        val peerFlow = MutableStateFlow(listOf(peerOnline))
        val component = buildComponentWithPeers(peerFlow = peerFlow, coroutineScope = backgroundScope)
        runCurrent()

        val before = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(before)

        peerFlow.value = listOf(peerOnline.copy(isOnline = false))
        runCurrent()

        val after = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertEquals(before, after)
    }

    @Test
    fun `offline-online polarity — component stays same instance and final state is online`() = runTest {
        val peerOnline = Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = true)
        val peerFlow = MutableStateFlow(listOf(peerOnline))
        val component = buildComponentWithPeers(peerFlow = peerFlow, coroutineScope = backgroundScope)
        runCurrent()

        val retained = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(retained)

        peerFlow.value = listOf(peerOnline.copy(isOnline = false))
        runCurrent()
        assertSame(retained, component.peerTransferComponent(deviceA.toPeerIdentity()))
        assertEquals(false, retained.state.value.isOnline)

        peerFlow.value = listOf(peerOnline.copy(isOnline = true))
        runCurrent()
        assertSame(retained, component.peerTransferComponent(deviceA.toPeerIdentity()))
        assertEquals(true, retained.state.value.isOnline)
    }

    @Test
    fun `device rename reactivity — state reflects updated device name on retained component`() = runTest {
        val peerFlow = MutableStateFlow(
            listOf(Peer(id = deviceA.toPeerIdentity(), device = deviceA, isOnline = true)),
        )
        val component = buildComponentWithPeers(peerFlow = peerFlow, coroutineScope = backgroundScope)
        runCurrent()

        val retained = component.peerTransferComponent(deviceA.toPeerIdentity())
        assertNotNull(retained)
        assertEquals(deviceA.name, retained.state.value.device.name)

        val renamedDevice = deviceA.copy(name = "RenamedDevice")
        peerFlow.value = listOf(Peer(id = deviceA.toPeerIdentity(), device = renamedDevice, isOnline = true))
        runCurrent()

        assertSame(retained, component.peerTransferComponent(deviceA.toPeerIdentity()))
        assertEquals("RenamedDevice", retained.state.value.device.name)
    }

    @Test
    fun `isPickerModeChooserNeeded true — onCardClick sets showPickerModeChooser without invoking picker`() = runTest {
        val picker = FakeFilePicker(result = emptyList())
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(
            flow = flow,
            coroutineScope = backgroundScope,
            isPickerModeChooserNeeded = true,
            filePicker = picker,
        )
        runCurrent()

        val row = component.state.value.rows
            .first()
        row.onCardClick()
        runCurrent()

        assertEquals(true, component.state.value.showPickerModeChooser)
        assertFalse(picker.pickFilesCalled, "picker must not be invoked before mode is chosen")
        assertFalse(picker.pickPhotosCalled)
    }

    @Test
    fun `isPickerModeChooserNeeded — onChoosePickerMode Photos routes pick to tapped row`() = runTest {
        val picker = FakeFilePicker(result = listOf(FakeFileSource("img.png", 50L)))
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(
            flow = flow,
            coroutineScope = backgroundScope,
            isPickerModeChooserNeeded = true,
            filePicker = picker,
        )
        runCurrent()

        val row = component.state.value.rows
            .first()
        row.onCardClick()
        runCurrent()
        assertEquals(true, component.state.value.showPickerModeChooser)

        component.onChoosePickerMode(PickKind.Photos)
        runCurrent()

        assertEquals(false, component.state.value.showPickerModeChooser)
        assertTrue(picker.pickPhotosCalled, "Photos pick must be routed to the tapped row's picker")
        assertIs<PeerTransferState.Sent>(row.state.value.transfer)
    }

    @Test
    fun `isPickerModeChooserNeeded false — onCardClick invokes picker directly without showing chooser`() = runTest {
        val picker = FakeFilePicker(result = emptyList())
        val flow = MutableStateFlow(listOf(deviceA))
        val component = buildComponent(
            flow = flow,
            coroutineScope = backgroundScope,
            isPickerModeChooserNeeded = false,
            filePicker = picker,
        )
        runCurrent()

        val row = component.state.value.rows
            .first()
        row.onCardClick()
        runCurrent()

        assertEquals(false, component.state.value.showPickerModeChooser)
        assertTrue(picker.pickFilesCalled, "picker must be invoked directly when chooser is not needed")
    }

    @Test
    fun `onChoosePickerMode without prior tap throws IllegalStateException`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope, isPickerModeChooserNeeded = true)

        var thrown: IllegalStateException? = null
        try {
            component.onChoosePickerMode()
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull(thrown)
    }
}
