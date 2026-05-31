package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.peer.FakePeersRepository
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSendDispatcherTest {
    private val deviceA = Device(name = "PeerA", host = "10.0.0.1", port = 8080)
    private val deviceB = Device(name = "PeerB", host = "10.0.0.2", port = 8080)

    private val peerA = Peer(id = PeerIdentity("peer-a"), device = deviceA, isOnline = true)
    private val peerB = Peer(id = PeerIdentity("peer-b"), device = deviceB, isOnline = true)

    /**
     * Builds a registry whose engine factory uses the given [scope] (test scheduler),
     * bypassing the [PeerTransferEngineRegistry]'s own Dispatchers.Default engine scope.
     */
    private fun buildRegistry(scope: CoroutineScope, store: PeerPreferencesStore): PeerTransferEngineRegistry =
        PeerTransferEngineRegistry(
            appScope = scope,
            engineFactory = { peer, _ ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = fakeBatchSender(),
                    inboundEvents = MutableSharedFlow(),
                    scope = scope,
                    peerPreferencesStore = store,
                )
            },
        )

    private fun buildDispatcher(
        peersRepo: FakePeersRepository,
        pendingRepo: PendingFilesRepository,
        store: PeerPreferencesStore,
        registry: PeerTransferEngineRegistry,
        scope: CoroutineScope,
    ) = AutoSendDispatcher(
        peersRepository = peersRepo,
        pendingFilesRepository = pendingRepo,
        peerPreferencesStore = store,
        engineRegistry = registry,
        scope = scope,
    )

    @Test
    fun `single online peer with auto-send ON triggers transfer on pending arrival`() = runTest {
        val store = FakePeerPreferencesStore()
        store.setAutoSendSync(peerA.id, true)
        val peersRepo = FakePeersRepository(MutableStateFlow(listOf(peerA)))
        val pendingRepo = PendingFilesRepository()
        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, store, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        repeat(4) { runCurrent() }

        assertIs<PeerTransferState.Sent>(registry.engineFor(peerA.id).state.value)
        assertNull(pendingRepo.summary.value)
    }

    @Test
    fun `single online peer with auto-send OFF does not trigger`() = runTest {
        val store = FakePeerPreferencesStore()
        val peersRepo = FakePeersRepository(MutableStateFlow(listOf(peerA)))
        val pendingRepo = PendingFilesRepository()
        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, store, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        repeat(4) { runCurrent() }

        assertIs<PeerTransferState.Idle>(registry.engineFor(peerA.id).state.value)
        assertNotNull(pendingRepo.summary.value)
    }

    @Test
    fun `two online peers with auto-send ON for both do not trigger`() = runTest {
        val store = FakePeerPreferencesStore()
        store.setAutoSendSync(peerA.id, true)
        store.setAutoSendSync(peerB.id, true)
        val peersRepo = FakePeersRepository(MutableStateFlow(listOf(peerA, peerB)))
        val pendingRepo = PendingFilesRepository()
        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, store, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        repeat(4) { runCurrent() }

        assertIs<PeerTransferState.Idle>(registry.engineFor(peerA.id).state.value)
        assertIs<PeerTransferState.Idle>(registry.engineFor(peerB.id).state.value)
        assertNotNull(pendingRepo.summary.value)
    }

    @Test
    fun `two online peers with auto-send ON only for one do not trigger`() = runTest {
        val store = FakePeerPreferencesStore()
        store.setAutoSendSync(peerA.id, true)
        val peersRepo = FakePeersRepository(MutableStateFlow(listOf(peerA, peerB)))
        val pendingRepo = PendingFilesRepository()
        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, store, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        repeat(4) { runCurrent() }

        assertIs<PeerTransferState.Idle>(registry.engineFor(peerA.id).state.value)
        assertIs<PeerTransferState.Idle>(registry.engineFor(peerB.id).state.value)
        assertNotNull(pendingRepo.summary.value)
    }

    @Test
    fun `zero online peers do not trigger`() = runTest {
        val store = FakePeerPreferencesStore()
        store.setAutoSendSync(peerA.id, true)
        val peersRepo = FakePeersRepository(MutableStateFlow(emptyList()))
        val pendingRepo = PendingFilesRepository()
        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, store, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        repeat(4) { runCurrent() }

        assertNotNull(pendingRepo.summary.value)
    }

    @Test
    fun `auto-send bails when a second peer comes online during preference read`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val peersFlow = MutableStateFlow(listOf(peerA))
        val peersRepo = FakePeersRepository(peersFlow)
        val pendingRepo = PendingFilesRepository()
        val store = FakePeerPreferencesStore()

        val gatedStore = object : PeerPreferencesStore {
            override fun observeAutoSend(peer: PeerIdentity) = flow {
                gate.await()
                emit(true)
            }

            override suspend fun setAutoSend(peer: PeerIdentity, enabled: Boolean) = Unit

            override suspend fun autoSendEnabledFor(peer: PeerIdentity) = true
        }

        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, gatedStore, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()

        peersFlow.value = listOf(peerA, peerB)
        gate.complete(Unit)
        repeat(4) { runCurrent() }

        assertIs<PeerTransferState.Idle>(registry.engineFor(peerA.id).state.value)
        assertNotNull(pendingRepo.summary.value)
    }

    @Test
    fun `auto-send bails when sources are cleared during preference read`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val peersRepo = FakePeersRepository(MutableStateFlow(listOf(peerA)))
        val pendingRepo = PendingFilesRepository()
        val store = FakePeerPreferencesStore()

        val gatedStore = object : PeerPreferencesStore {
            override fun observeAutoSend(peer: PeerIdentity) = flow {
                gate.await()
                emit(true)
            }

            override suspend fun setAutoSend(peer: PeerIdentity, enabled: Boolean) = Unit

            override suspend fun autoSendEnabledFor(peer: PeerIdentity) = true
        }

        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, gatedStore, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()

        pendingRepo.clear()
        gate.complete(Unit)
        repeat(4) { runCurrent() }

        assertIs<PeerTransferState.Idle>(registry.engineFor(peerA.id).state.value)
        assertNull(pendingRepo.summary.value)
    }

    @Test
    fun `auto-send bails when engine is no longer Idle after preference read`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val peersRepo = FakePeersRepository(MutableStateFlow(listOf(peerA)))
        val pendingRepo = PendingFilesRepository()
        val store = FakePeerPreferencesStore()

        val gatedStore = object : PeerPreferencesStore {
            override fun observeAutoSend(peer: PeerIdentity) = flow {
                gate.await()
                emit(true)
            }

            override suspend fun setAutoSend(peer: PeerIdentity, enabled: Boolean) = Unit

            override suspend fun autoSendEnabledFor(peer: PeerIdentity) = true
        }

        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, gatedStore, registry, backgroundScope).start()
        runCurrent()

        val otherSources = listOf(FakeFileSource("other.txt", 50L))
        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        runCurrent()

        registry.engineFor(peerA.id).startOutbound(otherSources)
        repeat(4) { runCurrent() }

        gate.complete(Unit)
        repeat(4) { runCurrent() }

        // Engine already transitioned; auto-send must not start a second batch or clear pending.
        assertNotNull(pendingRepo.summary.value)
    }

    @Test
    fun `auto-send pipeline recovers after preference read IOException`() = runTest {
        var callCount = 0
        val peersRepo = FakePeersRepository(MutableStateFlow(listOf(peerA)))
        val pendingRepo = PendingFilesRepository()
        val store = FakePeerPreferencesStore()

        val failOnceStore = object : PeerPreferencesStore {
            override fun observeAutoSend(peer: PeerIdentity) = flow<Boolean> {
                callCount++
                if (callCount == 1) throw java.io.IOException("DataStore unavailable")
                emit(true)
            }

            override suspend fun setAutoSend(peer: PeerIdentity, enabled: Boolean) = Unit

            override suspend fun autoSendEnabledFor(peer: PeerIdentity) = true
        }

        val registry = buildRegistry(backgroundScope, store)
        buildDispatcher(peersRepo, pendingRepo, failOnceStore, registry, backgroundScope).start()
        runCurrent()

        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        repeat(4) { runCurrent() }

        // First emission: IOException caught → emit(false) → no trigger.
        assertIs<PeerTransferState.Idle>(registry.engineFor(peerA.id).state.value)
        assertNotNull(pendingRepo.summary.value)

        // Second emission: store succeeds → transfer starts.
        pendingRepo.clear()
        pendingRepo.setPending(PendingFilesSummary(1, 100L), listOf(FakeFileSource("a.txt", 100L)))
        repeat(4) { runCurrent() }

        assertIs<PeerTransferState.Sent>(registry.engineFor(peerA.id).state.value)
        assertNull(pendingRepo.summary.value)
    }
}
