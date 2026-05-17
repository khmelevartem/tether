package com.tubetoast.tether.presentation.transfer

data class FailedFile(
    val name: String,
    val reason: FailureReason,
)

enum class FailureReason { Unreadable, ReceiverWriteFailed, ConnectionLost }
