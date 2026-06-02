package com.tubetoast.tether.network

fun interface PairingConfirmationHandler {
    /** Show [pin] to the user along with [remoteName]; return true if user confirms. */
    suspend fun confirmPairing(pin: Int, remoteName: String): Boolean
}
