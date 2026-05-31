package com.tubetoast.tether.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.random.Random

// TODO — replace interim random hex with EC P-256 public key fingerprint (#11)
private val KEY = stringPreferencesKey("device_fingerprint")

class DeviceIdentityStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getOrCreate(): String {
        val existing = dataStore.data.map { it[KEY] }.first()
        if (existing != null) return existing
        val generated = Random.Default.nextBytes(16).toHexString()
        dataStore.edit { it[KEY] = generated }
        return generated
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { byte ->
        byte
            .toInt()
            .and(0xFF)
            .toString(16)
            .padStart(2, '0')
    }
