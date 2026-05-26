package com.tubetoast.tether.transfer

sealed interface FailureReason {
    data object NetworkLost : FailureReason

    data object PeerUnreachable : FailureReason

    data class Unreadable(
        val filename: String,
    ) : FailureReason

    data class ReceiverWriteFailed(
        val httpStatus: Int?,
    ) : FailureReason

    data object ReceiverSuspended : FailureReason

    data object TransferCancelled : FailureReason

    data object CancelledByUser : FailureReason
}
