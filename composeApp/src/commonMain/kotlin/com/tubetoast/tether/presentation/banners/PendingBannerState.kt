package com.tubetoast.tether.presentation.banners

import com.tubetoast.tether.transfer.PendingFilesSummary

sealed interface PendingBannerState {
    data object Hidden : PendingBannerState

    data class Default(
        val summary: PendingFilesSummary,
        val dropFeedback: Boolean,
    ) : PendingBannerState

    /**
     * [announcementTick] increments on each repeat tap so that the data-class identity changes
     * and Compose recomposes the banner. The banner composable maps it to a `stateDescription`
     * semantics property so the semantics tree always differs between recompositions, giving
     * the live-region machinery a content change to fire on — even when [peerName] is unchanged.
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
