package com.tubetoast.tether.security

expect class TrustedDeviceStore {
    fun isTrusted(deviceId: String): Boolean

    fun saveTrustedKey(deviceId: String, publicKey: ByteArray)

    fun getPublicKey(deviceId: String): ByteArray?
}
