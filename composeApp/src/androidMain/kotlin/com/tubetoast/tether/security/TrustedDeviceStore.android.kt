package com.tubetoast.tether.security

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Trust-store contents are *public* keys of paired peers — not secrets — so the storage layer
// only needs persistence and integrity, not confidentiality. AndroidX DataStore replaces the
// deprecated EncryptedSharedPreferences (security-crypto is on life support; the suggested
// MasterKey/security-crypto 1.1 line stalled — see the 2026 migration guide). Tink AEAD on
// top of DataStore is a follow-up if we ever need at-rest confidentiality.
private val Context.trustedDevicesDataStore: androidx.datastore.core.DataStore<Preferences> by
    preferencesDataStore(name = "tether_trusted_devices")

actual class TrustedDeviceStore(
    private val context: Context,
) {
    private val store get() = context.trustedDevicesDataStore

    actual fun isTrusted(deviceId: String): Boolean = getPublicKey(deviceId) != null

    actual fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        runBlocking {
            store.edit { prefs ->
                prefs[stringPreferencesKey(deviceId)] = publicKey.joinToString(",")
            }
        }
    }

    actual fun getPublicKey(deviceId: String): ByteArray? {
        val value = runBlocking {
            store.data.first()[stringPreferencesKey(deviceId)]
        } ?: return null
        if (value.isEmpty()) return ByteArray(0)
        return try {
            value.split(",").map { it.trim().toByte() }.toByteArray()
        } catch (e: Exception) {
            System.err.println("ERROR: corrupted trusted key for '$deviceId' — ${e.message}")
            null
        }
    }
}
