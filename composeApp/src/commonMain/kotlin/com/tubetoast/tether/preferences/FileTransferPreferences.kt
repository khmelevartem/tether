package com.tubetoast.tether.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FileTransferPreferences {
    fun observeLargeSelectionWarning(): Flow<Boolean>

    suspend fun setLargeSelectionWarning(enabled: Boolean)

    fun observeSaveLocation(): Flow<String>

    /**
     * @throws UnsupportedOperationException on platforms where the save location is fixed (e.g. iOS).
     */
    suspend fun setSaveLocation(path: String)
}

class DefaultFileTransferPreferences(
    private val dataStore: DataStore<Preferences>,
    private val defaultSaveLocation: String = "",
    private val saveLocationWritable: Boolean = true,
) : FileTransferPreferences {
    override fun observeLargeSelectionWarning(): Flow<Boolean> =
        dataStore.data.map { it[KEY_LARGE_SELECTION_WARNING] ?: true }

    override fun observeSaveLocation(): Flow<String> =
        dataStore.data.map { it[KEY_SAVE_LOCATION] ?: defaultSaveLocation }

    override suspend fun setLargeSelectionWarning(enabled: Boolean) {
        dataStore.edit { it[KEY_LARGE_SELECTION_WARNING] = enabled }
    }

    override suspend fun setSaveLocation(path: String) {
        if (!saveLocationWritable) throw UnsupportedOperationException("Save location is fixed on this platform")
        dataStore.edit { it[KEY_SAVE_LOCATION] = path }
    }

    private companion object {
        val KEY_LARGE_SELECTION_WARNING = booleanPreferencesKey("large_selection_warning")
        val KEY_SAVE_LOCATION = stringPreferencesKey("save_location")
    }
}
