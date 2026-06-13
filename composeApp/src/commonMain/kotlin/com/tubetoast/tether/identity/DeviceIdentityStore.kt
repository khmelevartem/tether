package com.tubetoast.tether.identity

import com.tubetoast.tether.security.deviceIdFromPublicKey

class DeviceIdentityStore(
    private val publicKey: ByteArray,
) {
    fun fingerprint(): String = deviceIdFromPublicKey(publicKey)
}
