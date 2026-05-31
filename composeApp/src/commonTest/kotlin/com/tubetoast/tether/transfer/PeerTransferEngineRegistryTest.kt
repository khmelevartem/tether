package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferEngineRegistryTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    private fun buildRegistry(appScope: CoroutineScope): PeerTransferEngineRegistry =
        PeerTransferEngineRegistry(
            appScope = appScope,
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = fakeBatchSender(),
                    inboundEvents = MutableSharedFlow(),
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )

    @Test
    fun `engineFor returns the same engine for repeated calls with the same peer`() = runTest {
        val registry = buildRegistry(backgroundScope)

        val first = registry.engineFor(peerA)
        val second = registry.engineFor(peerA)

        assertEquals(first, second)
    }

    @Test
    fun `engineFor returns distinct engines for distinct peers`() = runTest {
        val registry = buildRegistry(backgroundScope)

        val engineA = registry.engineFor(peerA)
        val engineB = registry.engineFor(peerB)

        assertNotEquals(engineA, engineB)
    }

    @Test
    fun `evict removes engine so next engineFor creates a fresh instance`() = runTest {
        val registry = buildRegistry(backgroundScope)

        val before = registry.engineFor(peerA)
        registry.evict(peerA)
        val after = registry.engineFor(peerA)

        assertNotEquals(before, after)
    }

    @Test
    fun `evict cancels the evicted engine scope`() = runTest {
        var capturedEngineScope: CoroutineScope? = null
        val registry = PeerTransferEngineRegistry(
            appScope = backgroundScope,
            engineFactory = { peer, engineScope ->
                capturedEngineScope = engineScope
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = fakeBatchSender(),
                    inboundEvents = MutableSharedFlow(),
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )

        registry.engineFor(peerA)
        val scope = capturedEngineScope
        assertNotNull(scope)

        registry.evict(peerA)

        assertFalse(scope.isActive)
    }

    @Test
    fun `engines created in parallel for the same peer resolve to one instance`() = runTest {
        val registry = buildRegistry(backgroundScope)
        val threadCount = 8
        // CompletableDeferred broadcasts to all waiting threads at once, maximising the chance
        // that compareAndSet races across real Dispatchers.Default threads.
        val ready = Semaphore(permits = threadCount, acquiredPermits = threadCount)
        val go = CompletableDeferred<Unit>()

        val results = (1..threadCount)
            .map {
                async {
                    withContext(Dispatchers.Default) {
                        ready.release()
                        go.await()
                        registry.engineFor(peerA)
                    }
                }
            }

        repeat(threadCount) { ready.acquire() }
        go.complete(Unit)

        val engines = results.awaitAll()
        val expected = engines.first()
        engines.forEach { assertSame(expected, it) }
    }
}
