package com.tubetoast.tether.security

import java.security.MessageDigest

actual fun deviceIdFromPublicKey(publicKey: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(publicKey)
        .joinToString("") { "%02x".format(it) }
