package com.tubetoast.tether.security

// `open` so tests can substitute a throwing implementation to exercise the
// /pair → 500 contract per platform. The actual classes remain platform-specific.
expect open class TrustedDeviceStore {
    open fun isTrusted(deviceId: String): Boolean

    open fun saveTrustedKey(deviceId: String, publicKey: ByteArray)

    open fun getPublicKey(deviceId: String): ByteArray?
}
