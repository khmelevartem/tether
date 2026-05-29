package com.tubetoast.tether.presentation

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PeerListComponent(
    componentContext: ComponentContext,
    private val peersRepository: PeersRepository,
    private val peerTransferComponentFactory: (ComponentContext, Peer) -> PeerTransferComponent,
    private val peerPreferencesStore: PeerPreferencesStore? = null,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val scope = coroutineScope

    private val _state = MutableValue(PeerListState.empty())
    val state: Value<PeerListState> = _state

    private val children = mutableMapOf<PeerIdentity, PeerTransferComponent>()
    private val subscriptions = mutableMapOf<PeerIdentity, Cancellation>()
    private val seenPeers = mutableMapOf<PeerIdentity, Peer>()
    private var onlineIds: Set<PeerIdentity> = emptySet()

    private val mutablePendingFiles = MutableValue(PendingFilesSummary.NONE)
    val pendingFiles: Value<PendingFilesSummary> = mutablePendingFiles

    private val mutablePendingSources = MutableValue<List<FileSource>>(emptyList())

    private val mutableDropFeedback = MutableValue(false)
    val dropFeedback: Value<Boolean> = mutableDropFeedback

    private var dropFeedbackJob: Job? = null

    init {
        peersRepository.peers
            .onEach { peers ->
                onlineIds = peers.map { it.id }.toSet()
                peers.forEach { peer -> seenPeers[peer.id] = peer }
                ensureChildrenFor(peers)
                evictOfflineIdlePeers()
                rebuildState()
            }.launchIn(scope)
    }

    fun peerTransferComponent(peer: PeerIdentity): PeerTransferComponent? = children[peer]

    fun setPendingFiles(summary: PendingFilesSummary, sources: List<FileSource>) {
        mutablePendingFiles.value = summary
        mutablePendingSources.value = sources
    }

    fun clearPendingFiles() {
        mutablePendingFiles.value = PendingFilesSummary.NONE
        mutablePendingSources.value = emptyList()
    }

    fun onPeerTapped(peer: PeerIdentity) {
        val sources = mutablePendingSources.value
        if (sources.isEmpty()) return
        children[peer]?.startOutbound(sources)
        clearPendingFiles()
    }

    fun onDropDuringActiveTransfer() {
        dropFeedbackJob?.cancel()
        dropFeedbackJob = scope.launch {
            mutableDropFeedback.value = true
            delay(DROP_FEEDBACK_DURATION_MS)
            mutableDropFeedback.value = false
        }
    }

    fun observeAutoSend(peer: PeerIdentity): Flow<Boolean> =
        peerPreferencesStore?.observeAutoSend(peer) ?: flowOf(false)

    fun setAutoSend(peer: PeerIdentity, enabled: Boolean) {
        val store = peerPreferencesStore ?: return
        scope.launch { store.setAutoSend(peer, enabled) }
    }

    private fun ensureChildrenFor(peers: List<Peer>) {
        peers.forEach { peer ->
            if (peer.id !in children) {
                val child = peerTransferComponentFactory(childContext(peer.id.id), peer)
                children[peer.id] = child
                subscriptions[peer.id] = child.state.subscribe {
                    evictOfflineIdlePeers()
                    rebuildState()
                }
            }
        }
    }

    private fun evictOfflineIdlePeers() {
        val toRemove = children.keys.filter { id ->
            id !in onlineIds && children[id]?.state?.value is PeerTransferState.Idle
        }
        toRemove.forEach { id ->
            subscriptions.remove(id)?.cancel()
            children.remove(id)
        }
    }

    private fun rebuildState() {
        val rows = children.mapNotNull { (id, child) ->
            val peer = seenPeers[id] ?: return@mapNotNull null
            PeerRow(
                peer = peer.copy(isOnline = id in onlineIds),
                transferState = child.state.value,
            )
        }
        _state.update { PeerListState(rows) }
    }

    private companion object {
        const val DROP_FEEDBACK_DURATION_MS = 3_000L
    }
}
