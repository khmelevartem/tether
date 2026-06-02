package com.tubetoast.tether.security

private const val HEX = "0123456789abcdef"

actual fun deviceIdFromPublicKey(publicKey: ByteArray): String =
    sha256(publicKey).joinToString("") { byte ->
        val v = byte.toInt() and 0xff
        "${HEX[v ushr 4]}${HEX[v and 0xf]}"
    }
