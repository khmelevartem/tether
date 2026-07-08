package com.tubetoast.tether.transfer
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegistryActiveTransfersTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    private fun TestScope.buildRegistry(
        pauseChannelFor: (PeerIdentity) -> Channel<Unit> = { Channel() },
        inboundEventsFor: (PeerIdentity) -> MutableSharedFlow<ReceiveEvent> = { MutableSharedFlow() },
    ) = PeerTransferEngineRegistry(
        appScope = backgroundScope,
        engineFactory = { peer, engineScope ->
            PeerTransferEngine(
                peer = peer,
                batchSenderFactory = fakeBatchSender(pauseChannel = pauseChannelFor(peer)),
                inboundEvents = inboundEventsFor(peer),
                scope = engineScope,
                peerPreferencesStore = FakePeerPreferencesStore(),
                cancelBatch = { },
            )
        },
        engineDispatcher = StandardTestDispatcher(testScheduler),
    )

    /**
     * [advanceUntilIdle] never flushes work tagged as [TestScope.backgroundScope]; `.stateIn` on
     * [RegistryActiveTransfers] must run outside that tag — on the test scheduler, not a real
     * dispatcher — to be observable under virtual time.
     */
    private fun TestScope.foregroundScope() =
        CoroutineScope(Job(backgroundScope.coroutineContext[Job]) + StandardTestDispatcher(testScheduler))

    private fun outboundSource(): FileSource = FakeFileSource(name = "file.txt", sizeBytes = 1L)

    @Test
    fun `idle engine is not reported active`() = runTest {
        val registry = buildRegistry(pauseChannelFor = { Channel() })
        val activeTransfers = RegistryActiveTransfers(registry, foregroundScope())
        advanceUntilIdle()
        assertEquals(emptySet(), activeTransfers.peers.value)

        registry.engineFor(peerA)
        advanceUntilIdle()
        assertEquals(emptySet(), activeTransfers.peers.value, "idle engine must not count as active")
    }

    @Test
    fun `peer becomes active once its engine starts an outbound transfer`() = runTest {
        val registry = buildRegistry(pauseChannelFor = { Channel() })
        val activeTransfers = RegistryActiveTransfers(registry, foregroundScope())
        val engine = registry.engineFor(peerA)
        advanceUntilIdle()

        assertTrue(engine.startOutbound(listOf(outboundSource())))
        advanceUntilIdle()
        assertEquals(setOf(peerA), activeTransfers.peers.value)
    }

    @Test
    fun `multiple peers with in-flight transfers are all reported active`() = runTest {
        val registry = buildRegistry(pauseChannelFor = { Channel() })
        val activeTransfers = RegistryActiveTransfers(registry, foregroundScope())
        val engineA = registry.engineFor(peerA)
        val engineB = registry.engineFor(peerB)
        advanceUntilIdle()

        assertTrue(engineA.startOutbound(listOf(outboundSource())))
        advanceUntilIdle()
        assertEquals(setOf(peerA), activeTransfers.peers.value)

        assertTrue(engineB.startOutbound(listOf(outboundSource())))
        advanceUntilIdle()
        assertEquals(setOf(peerA, peerB), activeTransfers.peers.value)
    }

    @Test
    fun `peer becomes active once its engine receives an inbound transfer`() = runTest {
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val registry = buildRegistry(inboundEventsFor = { inboundEvents })
        val activeTransfers = RegistryActiveTransfers(registry, foregroundScope())
        val engine = registry.engineFor(peerA)
        advanceUntilIdle()

        inboundEvents.emit(ReceiveEvent.Started("file.txt", 1))
        advanceUntilIdle()

        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
        assertEquals(setOf(peerA), activeTransfers.peers.value)
    }

    @Test
    fun `peer stays active while its inbound engine is Reconnecting`() = runTest {
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val registry = buildRegistry(inboundEventsFor = { inboundEvents })
        val activeTransfers = RegistryActiveTransfers(registry, foregroundScope())
        val engine = registry.engineFor(peerA)
        advanceUntilIdle()

        inboundEvents.emit(ReceiveEvent.Started("file.txt", 1))
        runCurrent()
        inboundEvents.emit(ReceiveEvent.ConnectionLost(receivedSoFar = 0))
        // Not advanceUntilIdle: the reconnect countdown would elapse its full timeout and drive
        // the engine to Error. runCurrent propagates the Reconnecting link without advancing
        // virtual time into the countdown.
        runCurrent()

        val state = assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
        assertIs<PeerTransferState.InboundLink.Reconnecting>(state.link)
        assertEquals(setOf(peerA), activeTransfers.peers.value)
    }

    @Test
    fun `peer drops out of active transfers once its engine reaches a terminal state`() = runTest {
        val registry = buildRegistry(pauseChannelFor = { Channel() })
        val activeTransfers = RegistryActiveTransfers(registry, foregroundScope())
        val engine = registry.engineFor(peerA)
        advanceUntilIdle()

        assertTrue(engine.startOutbound(listOf(outboundSource())))
        advanceUntilIdle()
        assertEquals(setOf(peerA), activeTransfers.peers.value)

        engine.onCancel()
        advanceUntilIdle()

        assertIs<PeerTransferState.Cancelled>(engine.state.value)
        assertEquals(emptySet(), activeTransfers.peers.value)
    }
}
