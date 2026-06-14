package com.tubetoast.tether.presentation

import com.tubetoast.tether.presentation.transfer.PeerTransferComponent

data class PeerListState(
    val rows: List<PeerTransferComponent>,
    val showPickerModeChooser: Boolean = false,
) {
    companion object {
        fun empty() = PeerListState(emptyList())
    }
}
