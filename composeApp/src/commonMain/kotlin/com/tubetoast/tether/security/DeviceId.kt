package com.tubetoast.tether.security

/** Hex-encoded SHA-256 of the peer's public key — stable, collision-resistant trust-store key. */
expect fun deviceIdFromPublicKey(publicKey: ByteArray): String
