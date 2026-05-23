package com.tubetoast.tether.security

import com.tubetoast.tether.foundation.writeOrThrow
import platform.Foundation.NSUserDefaults
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "Tether.TrustedDeviceStore")

actual open class TrustedDeviceStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual open fun isTrusted(deviceId: String): Boolean = getPublicKey(deviceId) != null

    actual open fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        val encoded = publicKey.joinToString(",")
        defaults.writeOrThrow("tether_trust_$deviceId", encoded)
    }

    actual open fun getPublicKey(deviceId: String): ByteArray? {
        val value = defaults.stringForKey("tether_trust_$deviceId") ?: return null
        if (value.isEmpty()) return ByteArray(0)
        return try {
            value.split(",").map { it.trim().toByte() }.toByteArray()
        } catch (e: Exception) {
            log.error { "corrupted trusted key for '$deviceId' — ${e.message ?: "unknown error"}" }
            null
        }
    }
}
