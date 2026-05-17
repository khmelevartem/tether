package com.tubetoast.tether.presentation.transfer

data class BatchOutcome(
    val sent: Int,
    val total: Int,
    val failed: List<FailedFile>,
    val connectionLostMidway: Boolean,
)
