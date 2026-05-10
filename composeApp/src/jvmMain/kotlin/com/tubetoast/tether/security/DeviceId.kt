package com.tubetoast.tether.security

import java.security.MessageDigest

// Stable id derived from the peer's public key, not from the human-supplied deviceName.
// deviceName is unauthenticated and non-unique on the LAN, so using it as the trust-store
// key would let any peer overwrite trust by name collision.
fun deviceIdFromPublicKey(publicKey: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(publicKey)
        .joinToString("") { "%02x".format(it) }
