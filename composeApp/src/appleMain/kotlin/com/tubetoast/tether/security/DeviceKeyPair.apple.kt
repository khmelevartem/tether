@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private const val RAW_POINT_SIZE = 65
private val log = KydraLog.withTag(default = "DeviceKeyPair")

actual class DeviceKeyPair internal constructor(
    keychain: KeychainStore,
) {
    actual val publicKey: ByteArray = loadOrCreate(keychain)

    private fun loadOrCreate(keychain: KeychainStore): ByteArray {
        val privateKey = keychain.findPrivateKey()
        if (privateKey != null) {
            val rawPoint = keychain.extractPublicKeyBytes(privateKey)
            CFRelease(privateKey)
            if (rawPoint != null && isValidUncompressedPoint(rawPoint)) {
                return wrapInX509Spki(rawPoint)
            }
            log.warn { "Keychain entry corrupted, deleting and regenerating" }
            keychain.deleteEntry()
        }
        return generate(keychain)
    }

    private fun generate(keychain: KeychainStore): ByteArray {
        val privateKey = keychain.generatePrivateKey()
        val rawPoint = keychain.extractPublicKeyBytes(privateKey)
        CFRelease(privateKey)
        val validPoint = rawPoint?.takeIf { isValidUncompressedPoint(it) } ?: run {
            log.warn { "Failed to extract public key, retrying" }
            keychain.deleteEntry()
            val retryKey = keychain.generatePrivateKey()
            val retryPoint = keychain.extractPublicKeyBytes(retryKey)
            CFRelease(retryKey)
            retryPoint?.takeIf { isValidUncompressedPoint(it) }
                ?: throw IllegalStateException("DeviceKeyPair: failed to extract public key after retry")
        }
        return wrapInX509Spki(validPoint)
    }
}

private fun isValidUncompressedPoint(rawPoint: ByteArray) =
    rawPoint.size == RAW_POINT_SIZE && rawPoint[0] == 0x04.toByte()
