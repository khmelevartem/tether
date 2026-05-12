@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

actual fun deviceIdFromPublicKey(publicKey: ByteArray): String = memScoped {
    val digest = allocArray<kotlinx.cinterop.UByteVar>(CC_SHA256_DIGEST_LENGTH)
    if (publicKey.isEmpty()) {
        CC_SHA256(null, 0u, digest)
    } else {
        publicKey.usePinned { pinned ->
            CC_SHA256(pinned.addressOf(0), publicKey.size.convert(), digest)
        }
    }
    digest.readBytes(CC_SHA256_DIGEST_LENGTH).joinToString("") { byte ->
        val unsigned = byte.toInt() and 0xff
        val highNibble = unsigned ushr 4
        val lowNibble = unsigned and 0xf
        "${HEX[highNibble]}${HEX[lowNibble]}"
    }
}

private const val HEX = "0123456789abcdef"
