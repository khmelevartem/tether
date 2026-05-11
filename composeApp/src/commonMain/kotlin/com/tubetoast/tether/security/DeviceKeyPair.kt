package com.tubetoast.tether.security

/** Per-install identity persisted across launches; [publicKey] is the bytes a peer pins on pairing. */
expect class DeviceKeyPair {
    val publicKey: ByteArray
}
