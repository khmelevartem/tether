package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceListComponentTest {
    private val deviceA = Device(id = "a", name = "DeviceA", host = "192.168.1.1", port = 8080)
    private val deviceB = Device(id = "b", name = "DeviceB", host = "192.168.1.2", port = 8080)

    @Test
    fun `empty state when no devices discovered`() = runTest {
        val component = buildComponent(emptyList())
        assertEquals(emptyList(), component.state.value.devices)
    }

    @Test
    fun `emits devices from discovery`() = runTest {
        val flow = MutableStateFlow<List<Device>>(emptyList())
        val component = buildComponent(flow = flow)

        flow.value = listOf(deviceA, deviceB)
        runCurrent()

        assertEquals(listOf(deviceA, deviceB), component.state.value.devices)
    }

    @Test
    fun `updates state when device disappears`() = runTest {
        val flow = MutableStateFlow(listOf(deviceA, deviceB))
        val component = buildComponent(flow = flow)
        runCurrent()
        assertEquals(2, component.state.value.devices.size)

        flow.value = listOf(deviceA)
        runCurrent()

        assertEquals(listOf(deviceA), component.state.value.devices)
    }

    private fun buildComponent(
        initial: List<Device> = emptyList(),
        flow: MutableStateFlow<List<Device>> = MutableStateFlow(initial),
    ): DeviceListComponent {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle)
        lifecycle.resume()
        return DeviceListComponent(
            componentContext = context,
            discovery = FakeDeviceDiscovery(flow),
            coroutineScope = backgroundScope,
        )
    }
}

private class FakeDeviceDiscovery(
    private val flow: StateFlow<List<Device>>,
) : DeviceDiscovery {
    override val discoveredDevices: StateFlow<List<Device>> = flow

    override fun start(deviceName: String, port: Int) = Unit

    override fun stop() = Unit
}
