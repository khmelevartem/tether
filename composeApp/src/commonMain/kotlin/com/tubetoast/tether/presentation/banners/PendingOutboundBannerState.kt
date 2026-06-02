package com.tubetoast.tether.presentation.banners

import com.tubetoast.tether.transfer.PendingFilesSummary

sealed interface PendingOutboundBannerState {
    data object Hidden : PendingOutboundBannerState

    data class Default(
        val summary: PendingFilesSummary,
        val dropFeedback: Boolean,
    ) : PendingOutboundBannerState

    data class BusyPeer(
        val summary: PendingFilesSummary,
        val peerName: String,
    ) : PendingOutboundBannerState

    data class TerminalDisplay(
        val peerName: String,
    ) : PendingOutboundBannerState
}
