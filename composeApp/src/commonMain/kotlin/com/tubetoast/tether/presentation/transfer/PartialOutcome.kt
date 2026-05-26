package com.tubetoast.tether.presentation.transfer

sealed interface PartialOutcome {
    data object SenderCancelled : PartialOutcome

    data object ReceiverCancelled : PartialOutcome

    data object ConnectionLost : PartialOutcome

    data class FilesUnreadable(
        val count: Int,
    ) : PartialOutcome
}
