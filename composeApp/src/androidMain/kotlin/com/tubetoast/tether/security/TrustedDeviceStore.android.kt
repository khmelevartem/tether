package com.tubetoast.tether.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

// security-crypto 1.0.0 is the last stable release; MasterKeys/EncryptedSharedPreferences are
// deprecated in 1.1.0-alpha but the replacement (MasterKey.Builder) is not yet on a stable line.
// Pinned here until 1.1.0 ships stable; revisit when bumping the dep.
@Suppress("DEPRECATION")
actual class TrustedDeviceStore(
    private val context: Context,
) {
    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "tether_trusted_devices",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual fun isTrusted(deviceId: String): Boolean = getPublicKey(deviceId) != null

    actual fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        prefs.edit().putString(deviceId, publicKey.joinToString(",")).apply()
    }

    actual fun getPublicKey(deviceId: String): ByteArray? {
        val value = prefs.getString(deviceId, null) ?: return null
        if (value.isEmpty()) return ByteArray(0)
        return try {
            value.split(",").map { it.trim().toByte() }.toByteArray()
        } catch (e: Exception) {
            System.err.println("ERROR: corrupted trusted key for '$deviceId' — ${e.message}")
            null
        }
    }
}
