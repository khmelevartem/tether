package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePeerTransferRepository : PeerTransferRepository {
    private val states = mutableMapOf<PeerIdentity, MutableStateFlow<PeerTransferState>>()

    val cancelledPeers = mutableListOf<PeerIdentity>()
    val retriedPeers = mutableListOf<PeerIdentity>()
    val retriedFiles = mutableListOf<Pair<PeerIdentity, String>>()
    val cancelledFiles = mutableListOf<Pair<PeerIdentity, String>>()
    val dismissedPeers = mutableListOf<PeerIdentity>()
    val toggledPeers = mutableListOf<PeerIdentity>()
    val startedOutbound = mutableListOf<Pair<PeerIdentity, List<FileSource>>>()

    fun setState(peer: PeerIdentity, state: PeerTransferState) {
        flowFor(peer).value = state
    }

    override fun observe(peer: PeerIdentity): StateFlow<PeerTransferState> = flowFor(peer).asStateFlow()

    override fun startOutbound(peer: PeerIdentity, sources: List<FileSource>) {
        startedOutbound += peer to sources
    }

    override fun cancel(peer: PeerIdentity) {
        cancelledPeers += peer
    }

    override fun retry(peer: PeerIdentity) {
        retriedPeers += peer
    }

    override fun retryFile(peer: PeerIdentity, name: String) {
        retriedFiles += peer to name
    }

    override fun cancelFile(peer: PeerIdentity, name: String) {
        cancelledFiles += peer to name
    }

    override fun dismiss(peer: PeerIdentity) {
        dismissedPeers += peer
    }

    override fun toggleExpanded(peer: PeerIdentity) {
        toggledPeers += peer
    }

    private fun flowFor(peer: PeerIdentity): MutableStateFlow<PeerTransferState> =
        states.getOrPut(peer) { MutableStateFlow(PeerTransferState.Idle(peer)) }
}
