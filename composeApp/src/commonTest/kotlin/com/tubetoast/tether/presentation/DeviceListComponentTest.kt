package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.presentation.transfer.PeerRowProjection
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListComponentTest {
    private val deviceA = Device(name = "DeviceA", host = "192.168.1.1", port = 8080)
    private val deviceB = Device(name = "DeviceB", host = "192.168.1.2", port = 8080)

    @Test
    fun `empty state when no devices discovered`() = runTest {
        val component = buildComponent(emptyList(), coroutineScope = backgroundScope)
        assertEquals(emptyList(), component.state.value.devices)
    }

    @Test
    fun `emits devices from discovery`() = runTest {
        val flow = MutableStateFlow<List<Device>>(emptyList())
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)

        flow.value = listOf(deviceA, deviceB)
        runCurrent()

        assertEquals(listOf(deviceA, deviceB), component.state.value.devices)
    }

    @Test
    fun `updates state when device disappears`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA, deviceB))
        val component = buildComponent(flow = flow, coroutineScope = backgroundScope)
        runCurrent()
        assertEquals(2, component.state.value.devices.size)

        flow.value = listOf(deviceA)
        runCurrent()

        assertEquals(listOf(deviceA), component.state.value.devices)
    }

    @Test
    fun `rows reflect registry projections`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA))
        val registryRows = MutableValue<Map<PeerIdentity, PeerRowProjection>>(emptyMap())
        val component = buildComponent(flow = flow, registryRows = registryRows, coroutineScope = backgroundScope)
        runCurrent()

        val transferState = PeerTransferState.Idle(deviceA.toPeerIdentity())
        registryRows.value = mapOf(
            deviceA.toPeerIdentity() to PeerRowProjection(state = transferState, isOnline = true),
        )

        val row = component.state.value.rows
            .first()
        assertEquals(deviceA, row.device)
        assertIs<PeerTransferState.Idle>(row.transferState)
        assertEquals(true, row.isOnline)
    }

    private fun buildComponent(
        initial: List<Device> = emptyList(),
        flow: MutableStateFlow<List<Device>> = MutableStateFlow(initial),
        registryRows: MutableValue<Map<PeerIdentity, PeerRowProjection>> = MutableValue(emptyMap()),
        coroutineScope: CoroutineScope,
    ): DeviceListComponent {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle)
        lifecycle.resume()
        return DeviceListComponent(
            componentContext = context,
            discovery = FakeDeviceDiscovery(flow),
            registryRows = registryRows,
            coroutineScope = coroutineScope,
        )
    }
}
