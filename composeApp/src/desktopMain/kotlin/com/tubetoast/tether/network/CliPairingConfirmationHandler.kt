package com.tubetoast.tether.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CliPairingConfirmationHandler(
    private val output: (String) -> Unit = ::println,
    private val input: suspend () -> String? = { withContext(Dispatchers.IO) { readLine() } },
) : PairingConfirmationHandler {
    override suspend fun confirmPairing(pin: Int, remoteName: String): Boolean {
        output("[pair] PIN: ${pin.toString().padStart(4, '0')} — '$remoteName' is requesting to pair")
        output("[pair] Confirm? (y/n): ")
        val line = input()
        return line?.trim()?.lowercase() == "y"
    }
}
