package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.MutableSharedFlow

class FakePeerTransferDataSource(
    private val senderProvider: (PeerIdentity) -> BatchSender,
) : PeerTransferDataSource {
    override val inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 16)

    override fun outboundSender(peer: PeerIdentity): BatchSender = senderProvider(peer)
}
