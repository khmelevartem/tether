package com.tubetoast.tether.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "TrustedDeviceStore")

// Stored values are public keys, so confidentiality at rest is intentionally not provided.
class DefaultTrustedDeviceStore(
    private val dataStore: DataStore<Preferences>,
) : TrustedDeviceStore {
    override suspend fun isTrusted(deviceId: String): Boolean = getPublicKey(deviceId) != null

    override suspend fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("trust:$deviceId")] = publicKey.joinToString(",")
        }
    }

    // Write-side errors propagate so /pair returns 500 instead of falsely
    // claiming we trust a peer we failed to persist.
    override suspend fun getPublicKey(deviceId: String): ByteArray? {
        val value = try {
            dataStore.data.first()[stringPreferencesKey("trust:$deviceId")]
        } catch (e: Exception) {
            log.error { "failed to read trusted key for '$deviceId' — ${e.message}" }
            return null
        } ?: return null
        if (value.isEmpty()) return ByteArray(0)
        return try {
            value.split(",").map { it.trim().toByte() }.toByteArray()
        } catch (e: Exception) {
            log.error { "corrupted trusted key for '$deviceId' — ${e.message}" }
            null
        }
    }
}
