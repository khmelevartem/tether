package com.tubetoast.tether.presentation.banners

import com.tubetoast.tether.transfer.PendingFilesSummary

sealed interface PendingBannerState {
    data object Hidden : PendingBannerState

    data class Default(
        val summary: PendingFilesSummary,
        val dropFeedback: Boolean,
    ) : PendingBannerState

    /**
     * [announcementTick] increments on each repeat tap to force Compose recomposition
     * and re-trigger the assertive live-region announcement even when the peer name is unchanged.
     */
    data class BusyPeer(
        val summary: PendingFilesSummary,
        val peerName: String,
        val announcementTick: Int,
    ) : PendingBannerState

    /**
     * [announcementTick] — see [BusyPeer].
     */
    data class TerminalDisplay(
        val peerName: String,
        val announcementTick: Int,
    ) : PendingBannerState
}
