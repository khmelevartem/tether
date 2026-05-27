package com.tubetoast.tether.network

import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferDataSource
import com.tubetoast.tether.transfer.ReceiveEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TransportPeerTransferDataSource(
    private val fileClient: FileClient,
    private val transferActivityTracker: TransferActivityTracker,
) : PeerTransferDataSource {
    private val _inboundEvents = MutableSharedFlow<ReceiveEvent>(extraBufferCapacity = 64)
    override val inboundEvents: Flow<ReceiveEvent> = _inboundEvents.asSharedFlow()

    suspend fun publish(event: ReceiveEvent) = _inboundEvents.emit(event)

    override fun outboundSender(peer: PeerIdentity): BatchSender =
        error("TODO(#191 Phase B): construct BatchSender from FileClient + ConnectionMonitor")
}
