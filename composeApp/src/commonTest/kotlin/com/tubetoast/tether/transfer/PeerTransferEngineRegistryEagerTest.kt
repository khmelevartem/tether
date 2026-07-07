package com.tubetoast.tether.transfer
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferEngineRegistryEagerTest {
    private val peerA = PeerIdentity("peer-a")

    /**
     * [advanceUntilIdle] never flushes work tagged as [TestScope.backgroundScope]; the registry's
     * eager-warming collector must run on the test scheduler, not a real dispatcher, to be
     * observable under virtual time.
     */
    private fun TestScope.foregroundScope() =
        CoroutineScope(Job(backgroundScope.coroutineContext[Job]) + StandardTestDispatcher(testScheduler))

    @Test
    fun `engine is warmed for a discovered peer before any engineFor call`() = runTest {
        val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)
        val peers = MutableStateFlow(listOf(peerA))
        val registry = PeerTransferEngineRegistry(
            appScope = foregroundScope(),
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = fakeBatchSender(),
                    inboundEvents = inboundEvents,
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                    cancelBatch = { },
                )
            },
            peers = peers,
            engineDispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        assertTrue(
            peerA in registry.engines.value,
            "registry should warm an engine for a discovered peer without an explicit engineFor call",
        )

        inboundEvents.emit(ReceiveEvent.Started("file.txt", 1))
        advanceUntilIdle()

        val engine = registry.engines.value.getValue(peerA)
        assertIs<PeerTransferState.ActiveInbound>(engine.state.value)
    }
}
