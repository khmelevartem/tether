package com.tubetoast.tether.security

/**
 * Long-lived EC P-256 key pair persisted across launches. Public part is exposed for the
 * pairing handshake; private part stays inside the actual implementation.
 */
expect class DeviceKeyPair {
    val publicKey: ByteArray
}
