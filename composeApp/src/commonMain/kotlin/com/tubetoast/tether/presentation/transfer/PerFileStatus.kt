package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.FailureReason

sealed interface PerFileStatus {
    val name: String
    val size: Long?

    data class Queued(
        override val name: String,
        override val size: Long?,
    ) : PerFileStatus

    data class InProgress(
        override val name: String,
        override val size: Long?,
        val bytesDone: Long,
    ) : PerFileStatus

    data class Done(
        override val name: String,
        override val size: Long?,
    ) : PerFileStatus

    data class Failed(
        override val name: String,
        override val size: Long?,
        val reason: FailureReason,
        val cancelledByUser: Boolean = false,
    ) : PerFileStatus
}
