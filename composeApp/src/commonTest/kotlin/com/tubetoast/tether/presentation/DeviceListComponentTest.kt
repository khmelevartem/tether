package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListComponentTest {
    private val deviceA = Device(id = "a", name = "DeviceA", host = "192.168.1.1", port = 8080)
    private val deviceB = Device(id = "b", name = "DeviceB", host = "192.168.1.2", port = 8080)

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

    private fun buildComponent(
        initial: List<Device> = emptyList(),
        flow: MutableStateFlow<List<Device>> = MutableStateFlow(initial),
        coroutineScope: CoroutineScope,
    ): DeviceListComponent {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle)
        lifecycle.resume()
        return DeviceListComponent(
            componentContext = context,
            discovery = FakeDeviceDiscovery(flow),
            coroutineScope = coroutineScope,
        )
    }
}
