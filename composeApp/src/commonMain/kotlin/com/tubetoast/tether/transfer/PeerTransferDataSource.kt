package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.Flow

interface PeerTransferDataSource {
    val inboundEvents: Flow<ReceiveEvent>

    fun outboundSender(peer: PeerIdentity): BatchSender
}
