package com.tubetoast.tether.security

interface TrustedDeviceStore {
    // Read methods are the planned pin-check surface for the TLS channel (see adr-channel-encryption.md).
    suspend fun isTrusted(deviceId: String): Boolean

    suspend fun saveTrustedKey(deviceId: String, publicKey: ByteArray)

    suspend fun getPublicKey(deviceId: String): ByteArray?
}
