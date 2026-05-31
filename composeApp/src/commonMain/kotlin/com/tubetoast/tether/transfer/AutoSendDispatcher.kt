package com.tubetoast.tether.transfer

import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.preferences.PeerPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn

/**
 * Domain-layer trigger: when pending files arrive (share-sheet / drag-drop / CLI hand-off)
 * and exactly one peer is online with per-peer auto-send ON, start the transfer immediately
 * and clear pending. Lives in the transfer layer so any surface that already has the engine
 * registry — UI Components, CLI, future surfaces — gets auto-send for free.
 */
class AutoSendDispatcher(
    private val peersRepository: PeersRepository,
    private val pendingFilesRepository: PendingFilesRepository,
    private val peerPreferencesStore: PeerPreferencesStore,
    private val engineRegistry: PeerTransferEngineRegistry,
    private val scope: CoroutineScope,
) {
    fun start() {
        combine(peersRepository.peers, pendingFilesRepository.pending) { peers, pending ->
            if (pending == null) return@combine
            val onlinePaired = peers.filter { it.isOnline }.map { it.id }
            val singleCandidate = onlinePaired.singleOrNull() ?: return@combine
            val autoSendEnabled = peerPreferencesStore
                .observeAutoSend(singleCandidate)
                .catch { emit(false) }
                .first()
            if (!autoSendEnabled) return@combine
            // mDNS may have added a peer, pending may have been cleared, or the engine may have transitioned out of Idle.
            val pendingAfter = pendingFilesRepository.pending.value ?: return@combine
            val onlineAfter = peersRepository.peers.value
                .filter { it.isOnline }
                .map { it.id }
            if (onlineAfter.singleOrNull() != singleCandidate) return@combine
            val engine = engineRegistry.engineFor(singleCandidate)
            if (engine.state.value !is PeerTransferState.Idle) return@combine
            engine.startOutbound(pendingAfter.sources)
            // CAS on the snapshot — a fresh setPending that landed between startOutbound and here survives.
            pendingFilesRepository.clearIfMatches(pendingAfter)
        }.launchIn(scope)
    }
}
