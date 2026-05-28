package com.tubetoast.tether.security

interface TrustedDeviceStore {
    suspend fun isTrusted(deviceId: String): Boolean

    suspend fun saveTrustedKey(deviceId: String, publicKey: ByteArray)

    suspend fun getPublicKey(deviceId: String): ByteArray?
}
