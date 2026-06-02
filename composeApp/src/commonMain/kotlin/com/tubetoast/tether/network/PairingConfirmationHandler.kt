package com.tubetoast.tether.network

fun interface PairingConfirmationHandler {
    suspend fun confirmPairing(pin: Int, remoteName: String): Boolean
}
