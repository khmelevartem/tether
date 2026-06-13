package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PeerTransferState

data class PeerCardState(
    val transfer: PeerTransferState,
    /** Has no effect when the card is not idle. */
    val expanded: Boolean,
    val largeConfirm: PendingLargeConfirm? = null,
    val isOnline: Boolean,
    val device: Device,
    val requestMobileChooser: Boolean = false,
)
