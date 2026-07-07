package com.tubetoast.tether.transfer
import com.tubetoast.tether.peer.FakePeersRepository
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineWarmerTest {
    private val peerA = Peer(
        id = PeerIdentity("peer-a"),
        device = Device(name = "PeerA", host = "10.0.0.1", port = 8080),
        isOnline = true,
    )

    /**
     * [advanceUntilIdle] never flushes work tagged as [TestScope.backgroundScope]; the warmer's
     * collector must run on the test scheduler, not a real dispatcher, to be observable under
     * virtual time.
     */
    private fun TestScope.foregroundScope() =
        CoroutineScope(Job(backgroundScope.coroutineContext[Job]) + StandardTestDispatcher(testScheduler))

    @Test
    fun `start warms an engine for a discovered peer without an explicit engineFor call`() = runTest {
        val peersRepository = FakePeersRepository(MutableStateFlow(listOf(peerA)))
        val registry = PeerTransferEngineRegistry(
            appScope = foregroundScope(),
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = fakeBatchSender(),
                    inboundEvents = MutableSharedFlow(),
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                    cancelBatch = { },
                )
            },
            engineDispatcher = StandardTestDispatcher(testScheduler),
        )

        EngineWarmer(peersRepository, registry, foregroundScope()).start()
        advanceUntilIdle()

        assertTrue(
            peerA.id in registry.engines.value,
            "warmer should create an engine for a discovered peer without an explicit engineFor call",
        )
    }
}
