@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private const val FILE_LEGACY_PLACEHOLDER = "device_public.key"
private const val RAW_POINT_SIZE = 65
private val log = KydraLog.withTag(default = "DeviceKeyPair")

actual class DeviceKeyPair internal constructor(
    configDir: String? = null,
    keychain: KeychainStore,
) {
    actual val publicKey: ByteArray

    init {
        val directory = configDir ?: defaultConfigDir()
        deleteLegacyPlaceholderIfPresent(directory)
        publicKey = loadOrCreate(keychain)
    }

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

    private fun deleteLegacyPlaceholderIfPresent(directory: String) {
        val legacyPath = "$directory/$FILE_LEGACY_PLACEHOLDER"
        if (NSFileManager.defaultManager.fileExistsAtPath(legacyPath)) {
            NSFileManager.defaultManager.removeItemAtPath(legacyPath, error = null)
            log.info { "Migrated: removed legacy placeholder $legacyPath" }
        }
    }
}

private fun isValidUncompressedPoint(rawPoint: ByteArray) =
    rawPoint.size == RAW_POINT_SIZE && rawPoint[0] == 0x04.toByte()

private fun defaultConfigDir(): String {
    val documentsDir = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String
        ?: error("DeviceKeyPair: NSDocumentDirectory unavailable")
    return "$documentsDir/Tether/security"
}
