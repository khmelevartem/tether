package com.tubetoast.tether.presentation.banners

import com.tubetoast.tether.transfer.PendingFilesSummary

sealed interface PendingBannerState {
    data object Hidden : PendingBannerState

    data class Default(
        val summary: PendingFilesSummary,
        val dropFeedback: Boolean,
    ) : PendingBannerState

    /**
     * Increments on every busy-tap so the data-class identity changes; Compose recomposes the
     * banner. When the [peerName] differs between taps the rendered copy differs and the
     * platform live region re-announces. Same-peer repeat taps stay silent — the rendered text
     * is identical and Android TalkBack / iOS VoiceOver only re-announce on text-content change.
     * Full re-announcement on same-peer repeat tap requires platform actuals
     * (View.announceForAccessibility / UIAccessibility.post(.announcement)) — not in scope.
     */
    data class BusyPeer(
        val summary: PendingFilesSummary,
        val peerName: String,
        val announcementTick: Int,
    ) : PendingBannerState

    /**
     * [announcementTick] — see [BusyPeer.announcementTick].
     */
    data class TerminalDisplay(
        val peerName: String,
        val announcementTick: Int,
    ) : PendingBannerState
}
