package com.tubetoast.tether.security

actual fun deviceIdFromPublicKey(publicKey: ByteArray): String =
    sha256(publicKey).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
