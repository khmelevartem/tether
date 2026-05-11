@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.memcpy

// Placeholder until a real EC P-256 keypair lands via Keychain (#9 defers signature verification).
// Until then the protocol treats these bytes as an opaque per-install identifier.
private const val PUBLIC_KEY_BYTES = 32
private const val FILE_PUBLIC = "device_public.key"

actual class DeviceKeyPair(
    configDir: String? = null,
) {
    actual val publicKey: ByteArray

    init {
        val directory = configDir ?: defaultConfigDir()
        ensureDirectory(directory)
        val publicPath = "$directory/$FILE_PUBLIC"
        publicKey = loadOrGenerate(publicPath)
    }

    private fun loadOrGenerate(publicPath: String): ByteArray {
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(publicPath)) {
            val existing = readFile(publicPath)
            if (existing != null && existing.size == PUBLIC_KEY_BYTES) return existing
            NSLog("WARN: device key corrupted (size=%lu), regenerating", (existing?.size ?: 0).toULong())
            fileManager.removeItemAtPath(publicPath, error = null)
        }
        val fresh = randomBytes(PUBLIC_KEY_BYTES)
        if (!writeFile(publicPath, fresh)) {
            error("DeviceKeyPair: failed to write $publicPath")
        }
        return fresh
    }

    private fun ensureDirectory(directory: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(directory)) {
            fileManager.createDirectoryAtPath(
                path = directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }

    private fun readFile(path: String): ByteArray? =
        NSData.dataWithContentsOfFile(path)?.toByteArray()

    private fun writeFile(path: String, bytes: ByteArray): Boolean {
        val data = bytes.toNSData() ?: return false
        return data.writeToFile(path, atomically = true)
    }
}

private fun randomBytes(size: Int): ByteArray = memScoped {
    val buffer = allocArray<kotlinx.cinterop.UByteVar>(size)
    val status = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), buffer)
    if (status != 0) error("DeviceKeyPair: SecRandomCopyBytes failed status=$status")
    buffer.readBytes(size)
}

private fun defaultConfigDir(): String {
    val documentsDir = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String
        ?: error("DeviceKeyPair: NSDocumentDirectory unavailable")
    return "$documentsDir/Tether/security"
}

private fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    val result = ByteArray(byteCount)
    if (byteCount == 0) return result
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
