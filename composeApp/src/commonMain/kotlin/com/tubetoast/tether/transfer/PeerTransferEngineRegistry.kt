package com.tubetoast.tether.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Engines survive Component destruction (mDNS drops, screen navigation). The only triggers
 * for engine death are an explicit eviction call by the caller and process death; an engine
 * for a permanently gone peer that nobody evicts lives until process death.
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

    private val _engines = MutableStateFlow<Map<PeerIdentity, PeerTransferEngine>>(emptyMap())
    val engines: StateFlow<Map<PeerIdentity, PeerTransferEngine>> = _engines.asStateFlow()

    fun engineFor(peer: PeerIdentity): PeerTransferEngine {
        while (true) {
            val current = entries.load()
            val existing = current[peer]
            if (existing != null) return existing.engine
            val engineScope = CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.Default)
            val newEntry = Entry(engineFactory(peer, engineScope), engineScope)
            val next = current + (peer to newEntry)
            if (entries.compareAndSet(current, next)) {
                _engines.update { it + (peer to newEntry.engine) }
                return newEntry.engine
            }
            engineScope.cancel()
        }
    }

    internal fun evict(peer: PeerIdentity) {
        while (true) {
            val current = entries.load()
            if (peer !in current) return
            val evicted = current[peer]
            val next = current - peer
            if (entries.compareAndSet(current, next)) {
                evicted?.scope?.cancel()
                _engines.update { it - peer }
                return
            }
        }
    }
}
