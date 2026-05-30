package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.PeerTransferState

data class PeerCardState(
    val transfer: PeerTransferState,
    /** Only meaningful when [transfer] is [PeerTransferState.Idle]. */
    val expanded: Boolean,
)
