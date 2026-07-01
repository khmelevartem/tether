package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegistryActiveTransfersTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    private fun buildRegistry(
        pauseChannelFor: (PeerIdentity) -> Channel<Unit>,
    ) = PeerTransferEngineRegistry(
        appScope = CoroutineScope(Dispatchers.Unconfined),
        engineFactory = { peer, engineScope ->
            PeerTransferEngine(
                peer = peer,
                batchSenderFactory = fakeBatchSender(pauseChannel = pauseChannelFor(peer)),
                scope = engineScope,
                peerPreferencesStore = FakePeerPreferencesStore(),
            )
        },
    )

    private fun outboundSource(): FileSource = FakeFileSource(name = "file.txt", sizeBytes = 1L)

    @Test
    fun `idle engine is not reported active`() = runTest {
        val registry = buildRegistry { Channel() }
        val activeTransfers = RegistryActiveTransfers(registry, backgroundScope)
        runCurrent()
        assertEquals(emptySet(), activeTransfers.peers.value)

        registry.engineFor(peerA)
        runCurrent()
        assertEquals(emptySet(), activeTransfers.peers.value, "idle engine must not count as active")
    }

    @Test
    fun `peer becomes active once its engine starts an outbound transfer`() = runTest {
        val registry = buildRegistry { Channel() }
        val activeTransfers = RegistryActiveTransfers(registry, backgroundScope)
        val engine = registry.engineFor(peerA)
        runCurrent()

        assertTrue(engine.startOutbound(listOf(outboundSource())))
        runCurrent()
        assertEquals(setOf(peerA.id), activeTransfers.peers.value)
    }

    @Test
    fun `multiple peers with in-flight transfers are all reported active`() = runTest {
        val registry = buildRegistry { Channel() }
        val activeTransfers = RegistryActiveTransfers(registry, backgroundScope)
        val engineA = registry.engineFor(peerA)
        val engineB = registry.engineFor(peerB)
        runCurrent()

        assertTrue(engineA.startOutbound(listOf(outboundSource())))
        runCurrent()
        assertEquals(setOf(peerA.id), activeTransfers.peers.value)

        assertTrue(engineB.startOutbound(listOf(outboundSource())))
        runCurrent()
        assertEquals(setOf(peerA.id, peerB.id), activeTransfers.peers.value)
    }
}
