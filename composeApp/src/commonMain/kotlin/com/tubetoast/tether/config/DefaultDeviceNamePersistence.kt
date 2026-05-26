package com.tubetoast.tether.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val KEY = stringPreferencesKey("device_name")

class DefaultDeviceNamePersistence(
    private val dataStore: DataStore<Preferences>,
) : DeviceNamePersistence {
    override suspend fun read(): String? = dataStore.data.map { it[KEY] }.first()

    override suspend fun write(value: String) {
        dataStore.edit { it[KEY] = value }
    }
}
