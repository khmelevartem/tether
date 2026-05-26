package com.tubetoast.tether.transfer

sealed interface BatchOutcome {
    data object AllSent : BatchOutcome

    data class PartialSent(
        val failed: List<String>,
    ) : BatchOutcome

    data class Cancelled(
        val sent: Int,
        val remaining: List<String>,
    ) : BatchOutcome

    data class Failed(
        val reason: TransferErrorReason,
        val sent: Int,
    ) : BatchOutcome
}
