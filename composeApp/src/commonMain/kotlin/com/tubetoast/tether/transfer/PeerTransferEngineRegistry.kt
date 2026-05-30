package com.tubetoast.tether.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Engines survive Component destruction (mDNS drops, screen navigation) and are evicted only
 * on explicit `evict` or process death. No caller wires `evict` yet — engines for permanently
 * gone peers stay until process death.
 */
@OptIn(ExperimentalAtomicApi::class)
class PeerTransferEngineRegistry(
    private val appScope: CoroutineScope,
    private val engineFactory: (PeerIdentity, CoroutineScope) -> PeerTransferEngine,
) {
    private data class Entry(
        val engine: PeerTransferEngine,
        val scope: CoroutineScope,
    )

    private val entries = AtomicReference(emptyMap<PeerIdentity, Entry>())

    fun engineFor(peer: PeerIdentity): PeerTransferEngine {
        while (true) {
            val current = entries.load()
            val existing = current[peer]
            if (existing != null) return existing.engine
            val engineScope = CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.Default)
            val newEntry = Entry(engineFactory(peer, engineScope), engineScope)
            val next = current + (peer to newEntry)
            if (entries.compareAndSet(current, next)) return newEntry.engine
            engineScope.coroutineContext[Job]?.cancel()
        }
    }

    internal fun evict(peer: PeerIdentity) {
        while (true) {
            val current = entries.load()
            if (peer !in current) return
            val evicted = current[peer]
            val next = current - peer
            if (entries.compareAndSet(current, next)) {
                evicted
                    ?.scope
                    ?.coroutineContext
                    ?.get(Job)
                    ?.cancel()
                return
            }
        }
    }
}
