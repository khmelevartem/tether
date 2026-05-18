package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FilePickerProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {
    private val peer = Device("Peer", "127.0.0.1", 8080)

    @Test
    fun processDeath_missingSourcesKey_fallsBackToDeviceList() = runTest {
        val lifecycle = LifecycleRegistry()
        val ctx = DefaultComponentContext(lifecycle)
        lifecycle.resume()
        val root = RootComponent(
            componentContext = ctx,
            discovery = FakeDeviceDiscovery(),
            fileClient = FileClient.default(),
            filePickerProvider = FilePickerProvider { null },
            childScopeOverride = backgroundScope,
        )

        root.pushTransferConfigForTest(
            RootComponent.Config.Transfer(peer = peer, sourcesKey = "missing-key"),
        )

        assertIs<RootComponent.Child.DeviceListChild>(
            root.stack.value.active.instance,
            "process-death restore with missing key must fall back to DeviceListChild",
        )
    }
}
