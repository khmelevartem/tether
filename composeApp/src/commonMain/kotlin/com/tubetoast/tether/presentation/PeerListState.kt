package com.tubetoast.tether.presentation

import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.presentation.transfer.PeerTransferState

data class PeerRow(
    val peer: Peer,
    val transferState: PeerTransferState,
)

data class PeerListState(
    val rows: List<PeerRow>,
) {
    companion object {
        fun empty() = PeerListState(emptyList())
    }
}
