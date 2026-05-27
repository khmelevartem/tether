package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.StateFlow

interface PeerTransferRepository {
    fun observe(peer: PeerIdentity): StateFlow<PeerTransferState>

    fun startOutbound(peer: PeerIdentity, sources: List<FileSource>)

    fun cancel(peer: PeerIdentity)

    fun retry(peer: PeerIdentity)

    fun retryFile(peer: PeerIdentity, name: String)

    fun cancelFile(peer: PeerIdentity, name: String)

    fun dismiss(peer: PeerIdentity)

    fun toggleExpanded(peer: PeerIdentity)
}
