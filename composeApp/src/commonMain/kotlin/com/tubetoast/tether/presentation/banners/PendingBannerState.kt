package com.tubetoast.tether.presentation.banners

import com.tubetoast.tether.transfer.PendingFilesSummary

sealed interface PendingBannerState {
    data object Hidden : PendingBannerState

    data class Default(
        val summary: PendingFilesSummary,
        val dropFeedback: Boolean,
    ) : PendingBannerState

    /**
     * Shown when the user tapped a peer whose engine is actively transferring.
     * [announcementTick] increments on each repeat tap to force Compose recomposition
     * and re-trigger the assertive live-region announcement even when the peer name is unchanged.
     */
    data class BusyPeer(
        val summary: PendingFilesSummary,
        val peerName: String,
        val announcementTick: Int,
    ) : PendingBannerState

    /**
     * Shown when the conflicting transfer on the chosen peer has reached a terminal state
     * (Sent / Received / Error / Cancelled). The user can now tap that peer to send.
     * [announcementTick] serves the same recomposition role as in [BusyPeer].
     */
    data class TerminalDisplay(
        val peerName: String,
        val announcementTick: Int,
    ) : PendingBannerState
}
