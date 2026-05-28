package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.fakeBatchSender
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class TransferRegistryTest {
    private val deviceA = Device(name = "DeviceA", host = "192.168.1.1", port = 8080)
    private val peerA = deviceA.toPeerIdentity()

    @Test
    fun `get returns stable instance across calls`() = runTest {
        val registry = buildRegistry()

        val first = registry.get(peerA)
        val second = registry.get(peerA)

        assertSame(first, second)
    }

    @Test
    fun `get registers peer in rows with correct online status`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val registry = buildRegistry(devices)
        runCurrent()

        registry.get(peerA)

        assertNotNull(registry.rows.value[peerA])
        assertEquals(true, registry.rows.value[peerA]?.isOnline)
    }

    @Test
    fun `peer disappears while state is Idle is evicted from rows`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val registry = buildRegistry(devices)
        runCurrent()

        registry.get(peerA)
        assertNotNull(registry.rows.value[peerA])

        devices.value = emptyList()
        runCurrent()

        assertNull(registry.rows.value[peerA])
    }

    @Test
    fun `peer disappears while state is non-Idle persists in rows`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val registry = buildRegistry(devices)
        runCurrent()

        val peerComponent = registry.get(peerA)
        peerComponent.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()

        devices.value = emptyList()
        runCurrent()

        assertNotNull(registry.rows.value[peerA])
    }

    @Test
    fun `peer evicted after dismissing terminal state while offline`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val registry = buildRegistry(devices)
        runCurrent()

        val peerComponent = registry.get(peerA)
        peerComponent.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()
        assertIs<PeerTransferState.Sent>(registry.rows.value[peerA]?.state)

        devices.value = emptyList()
        runCurrent()
        assertNotNull(registry.rows.value[peerA]) // still present: non-Idle while offline

        peerComponent.onDismiss() // transitions to Idle
        runCurrent()

        assertNull(registry.rows.value[peerA]) // evicted now that state is Idle and peer is offline
    }

    @Test
    fun `rows state reflects component state`() = runTest {
        val devices = MutableStateFlow(listOf(deviceA))
        val registry = buildRegistry(devices)
        runCurrent()

        val peerComponent = registry.get(peerA)
        assertIs<PeerTransferState.Idle>(registry.rows.value[peerA]?.state)

        peerComponent.startOutbound(listOf(FakeFileSource("file.txt", 10L)))
        runCurrent()

        assertIs<PeerTransferState.Sent>(registry.rows.value[peerA]?.state)
    }

    private fun TestScope.buildRegistry(
        devices: MutableStateFlow<List<Device>> = MutableStateFlow(emptyList()),
    ): TransferRegistry {
        val lifecycle = LifecycleRegistry().also { it.resume() }
        val context = DefaultComponentContext(lifecycle)
        return TransferRegistry(
            componentContext = context,
            peerComponentFactory = { childCtx, peer ->
                PeerTransferComponent(
                    componentContext = childCtx,
                    peer = peer,
                    batchSenderFactory = fakeBatchSender(),
                    inboundEvents = MutableSharedFlow(),
                    onShowDetailsCallback = {},
                    scope = backgroundScope,
                )
            },
            discoveredDevices = devices,
            scope = backgroundScope,
            mainDispatcher = StandardTestDispatcher(testScheduler),
        )
    }
}
