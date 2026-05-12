package com.tubetoast.tether.security

import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults

actual open class TrustedDeviceStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual open fun isTrusted(deviceId: String): Boolean = getPublicKey(deviceId) != null

    actual open fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        val encoded = publicKey.joinToString(",")
        defaults.setObject(encoded, forKey = "tether_trust_$deviceId")
        if (!defaults.synchronize()) {
            throw IllegalStateException("NSUserDefaults.synchronize() returned false for deviceId=$deviceId")
        }
    }

    actual open fun getPublicKey(deviceId: String): ByteArray? {
        val value = defaults.stringForKey("tether_trust_$deviceId") ?: return null
        if (value.isEmpty()) return ByteArray(0)
        return try {
            value.split(",").map { it.trim().toByte() }.toByteArray()
        } catch (e: Exception) {
            NSLog("ERROR: corrupted trusted key for '%s' — %s", deviceId, e.message ?: "unknown error")
            null
        }
    }
}
