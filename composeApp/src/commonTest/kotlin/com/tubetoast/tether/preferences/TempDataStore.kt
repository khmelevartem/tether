package com.tubetoast.tether.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.FileSystem
import okio.Path
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class TempDataStore(
    val file: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "test-${Random.nextLong()}.preferences_pb",
) {
    val scope = TestScope(UnconfinedTestDispatcher())
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope) { file }

    fun tearDown() {
        scope.cancel()
        FileSystem.SYSTEM.delete(file, mustExist = false)
    }
}
