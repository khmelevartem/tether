package com.tubetoast.tether.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.deviceNameDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "tether_device_name")

private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")

class DeviceNamePersistenceAndroid(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.deviceNameDataStore,
) : DeviceNamePersistence {
    override suspend fun read(): String? = dataStore.data.first()[KEY_DEVICE_NAME]

    override suspend fun write(value: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_NAME] = value
        }
    }
}
