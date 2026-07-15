package com.tubetoast.tether.presentation.banners

import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PeerConflictRelay {
    private val _busyTaps = MutableSharedFlow<PeerIdentity>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val busyTaps: SharedFlow<PeerIdentity> = _busyTaps.asSharedFlow()

    fun reportBusyTap(peer: PeerIdentity) {
        _busyTaps.tryEmit(peer)
    }
}
