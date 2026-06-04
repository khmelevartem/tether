package com.tubetoast.tether.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal class CliPairingConfirmationHandler(
    private val output: (String) -> Unit = ::println,
    // runInterruptible signals Thread.interrupt() when the server cancels the pairing wait on timeout.
    // stdin reads on most JVMs are not reliably interrupted, so a timed-out prompt may still consume
    // the next input line before returning. Acceptable for a developer-only CLI affordance.
    private val input: suspend () -> String? = { runInterruptible(Dispatchers.IO) { readLine() } },
) : PairingConfirmationHandler {
    override suspend fun confirmPairing(pin: Int, remoteName: String): Boolean {
        output("[pair] PIN: ${pin.toString().padStart(4, '0')} — '$remoteName' is requesting to pair")
        output("[pair] Confirm? (y/n): ")
        val line = input()
        return line?.trim()?.lowercase() == "y"
    }
}
