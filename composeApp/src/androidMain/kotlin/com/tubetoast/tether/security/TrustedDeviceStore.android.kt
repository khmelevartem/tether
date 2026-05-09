package com.tubetoast.tether.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

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
        try {
            prefs.edit().putString(deviceId, publicKey.joinToString(",")).apply()
        } catch (e: Exception) {
            System.err.println("ERROR: failed to save trusted key for '$deviceId' — ${e.message}")
        }
    }

    actual fun getPublicKey(deviceId: String): ByteArray? {
        val value = prefs.getString(deviceId, null) ?: return null
        if (value.isEmpty()) return ByteArray(0)
        return value.split(",").map { it.trim().toByte() }.toByteArray()
    }
}
