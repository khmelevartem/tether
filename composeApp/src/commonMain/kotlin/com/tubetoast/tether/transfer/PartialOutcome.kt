package com.tubetoast.tether.transfer

sealed interface PartialOutcome {
    data object SenderCancelled : PartialOutcome

    data object ReceiverCancelled : PartialOutcome

    data object ConnectionLost : PartialOutcome

    data object PeerUnreachable : PartialOutcome

    data object ReceiverWriteFailed : PartialOutcome

    data class FilesUnreadable(
        val count: Int,
    ) : PartialOutcome
}
