package com.tubetoast.tether.presentation

import com.tubetoast.tether.presentation.transfer.PeerTransferComponent

data class PeerRow(
    val transferComponent: PeerTransferComponent,
)

data class PeerListState(
    val rows: List<PeerRow>,
) {
    companion object {
        fun empty() = PeerListState(emptyList())
    }
}
