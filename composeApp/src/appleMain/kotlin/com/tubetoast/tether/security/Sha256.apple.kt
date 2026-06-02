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

internal actual fun sha256(data: ByteArray): ByteArray = memScoped {
    val digest = allocArray<kotlinx.cinterop.UByteVar>(CC_SHA256_DIGEST_LENGTH)
    if (data.isEmpty()) {
        CC_SHA256(null, 0u, digest)
    } else {
        data.usePinned { pinned ->
            CC_SHA256(pinned.addressOf(0), data.size.convert(), digest)
        }
    }
    digest.readBytes(CC_SHA256_DIGEST_LENGTH)
}
