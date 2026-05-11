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

// Issue #9 explicitly defers signature verification ("Шифрование трафика — следующий этап"),
// so the pairing handshake stores opaque public-key bytes without using them for crypto yet.
// 32 random bytes are stable across launches and large enough to look like an EC P-256 key to
// future verifiers. SecKey/Keychain migration is tracked separately (Apple Keychain follow-up).
private const val PUBLIC_KEY_BYTES = 32
private const val FILE_PUBLIC = "device_public.key"

actual class DeviceKeyPair(
    configDir: String? = null,
) {
    actual val publicKey: ByteArray

    init {
        val dir = configDir ?: defaultConfigDir()
        ensureDir(dir)
        val publicPath = "$dir/$FILE_PUBLIC"
        publicKey = loadOrGenerate(publicPath)
    }

    private fun loadOrGenerate(publicPath: String): ByteArray {
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(publicPath)) {
            val existing = readFile(publicPath)
            if (existing != null && existing.size == PUBLIC_KEY_BYTES) return existing
            NSLog("WARN: device key corrupted (size=%lu), regenerating", (existing?.size ?: 0).toULong())
            fm.removeItemAtPath(publicPath, error = null)
        }
        val fresh = randomBytes(PUBLIC_KEY_BYTES)
        if (!writeFile(publicPath, fresh)) {
            error("DeviceKeyPair: failed to write $publicPath")
        }
        return fresh
    }

    private fun ensureDir(dir: String) {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(dir)) {
            fm.createDirectoryAtPath(
                path = dir,
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
    val buf = allocArray<kotlinx.cinterop.UByteVar>(size)
    val rc = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), buf)
    if (rc != 0) error("DeviceKeyPair: SecRandomCopyBytes failed rc=$rc")
    buf.readBytes(size)
}

private fun defaultConfigDir(): String {
    val docs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String
        ?: error("DeviceKeyPair: NSDocumentDirectory unavailable")
    return "$docs/Tether/security"
}

private fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    val out = ByteArray(len)
    if (len == 0) return out
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}
