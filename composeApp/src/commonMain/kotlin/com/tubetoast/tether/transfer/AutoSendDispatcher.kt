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
        combine(peersRepository.peers, pendingFilesRepository.sources) { peers, sources ->
            if (sources.isEmpty()) return@combine
            val onlinePaired = peers.filter { it.isOnline }.map { it.id }
            val singleCandidate = onlinePaired.singleOrNull() ?: return@combine
            val autoSendEnabled = peerPreferencesStore
                .observeAutoSend(singleCandidate)
                .catch { emit(false) }
                .first()
            if (!autoSendEnabled) return@combine
            // mDNS may have added a peer, sources may have been cleared, or the engine may have transitioned out of Idle.
            val sourcesAfter = pendingFilesRepository.sources.value
            if (sourcesAfter.isEmpty()) return@combine
            val onlineAfter = peersRepository.peers.value
                .filter { it.isOnline }
                .map { it.id }
            if (onlineAfter.singleOrNull() != singleCandidate) return@combine
            val engine = engineRegistry.engineFor(singleCandidate)
            if (engine.state.value !is PeerTransferState.Idle) return@combine
            engine.startOutbound(sourcesAfter)
            // Conditional clear: a fresh setPending may have raced ahead while engine.startOutbound ran;
            // dropping it unconditionally would silently lose the user's newer share.
            pendingFilesRepository.clearIfMatches(sourcesAfter)
        }.launchIn(scope)
    }
}
