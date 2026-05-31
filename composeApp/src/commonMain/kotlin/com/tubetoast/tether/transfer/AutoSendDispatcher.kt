package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.peer.PeersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn

/**
 * Domain-layer trigger: when pending files arrive (share-sheet / drag-drop / CLI hand-off)
 * and exactly one peer is online with per-peer auto-send ON, start the transfer immediately
 * and clear pending. Lives next to `PeerTransferEngineRegistry` so any caller that has the
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
            val engine = engineRegistry.engineFor(singleCandidate)
            if (engine.state.value !is PeerTransferState.Idle) return@combine
            val autoSendEnabled = peerPreferencesStore
                .observeAutoSend(singleCandidate)
                .catch { emit(false) }
                .first()
            // Re-validate after the suspending preference read — mDNS may have added a peer,
            // sources may have been cleared, or the engine may have transitioned out of Idle.
            val sourcesAfter = pendingFilesRepository.sources.value
            if (sourcesAfter.isEmpty()) return@combine
            val onlineAfter = peersRepository.peers.value
                .filter { it.isOnline }
                .map { it.id }
            val decision = AutoSendRouter.route(onlineAfter) { it == singleCandidate && autoSendEnabled }
            val target = (decision as? RoutingDecision.AutoSend)?.peer ?: return@combine
            if (target != singleCandidate) return@combine
            if (engine.state.value !is PeerTransferState.Idle) return@combine
            engine.startOutbound(sourcesAfter)
            pendingFilesRepository.clear()
        }.launchIn(scope)
    }
}
