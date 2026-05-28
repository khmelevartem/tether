package com.tubetoast.tether.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "TrustedDeviceStore")

// Stored values are public keys, so confidentiality at rest is intentionally not provided.
// `open` so tests can substitute a throwing implementation to exercise the /pair → 500 contract.
open class TrustedDeviceStore(
    private val dataStore: DataStore<Preferences>,
) {
    open fun isTrusted(deviceId: String): Boolean = getPublicKey(deviceId) != null

    open fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("trust:$deviceId")] = publicKey.joinToString(",")
            }
        }
    }

    // Read-side parse errors are swallowed (logged + null) so a corrupted entry behaves as "untrusted"
    // and the peer re-pairs; write-side errors propagate so /pair returns 500 instead of falsely
    // claiming we trust a peer we failed to persist.
    open fun getPublicKey(deviceId: String): ByteArray? {
        val value = runBlocking {
            dataStore.data.first()[stringPreferencesKey("trust:$deviceId")]
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
