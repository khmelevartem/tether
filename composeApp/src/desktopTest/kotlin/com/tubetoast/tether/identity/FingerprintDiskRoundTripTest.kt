package com.tubetoast.tether.identity

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FingerprintDiskRoundTripTest {
    private lateinit var prefsFile: java.io.File

    @BeforeTest
    fun setup() {
        prefsFile = Files.createTempFile("tether-fp-roundtrip", ".preferences_pb").toFile()
        prefsFile.delete()
    }

    @AfterTest
    fun teardown() {
        prefsFile.delete()
    }

    @Test
    fun `fingerprint written by first DataStore is readable by second DataStore at same path`() =
        runTest(UnconfinedTestDispatcher()) {
            // The first DataStore gets its own job so it can be cancelled mid-test: DataStore releases the
            // file only when its scope completes, and the second factory call at the same path otherwise
            // throws "multiple DataStores active for the same file".
            val firstJob = Job()
            val firstStore = PreferenceDataStoreFactory.createWithPath(
                scope = CoroutineScope(
                    coroutineContext + firstJob,
                ),
            ) {
                prefsFile.toOkioPath()
            }
            val firstFingerprint = DeviceIdentityStore(DataStoreFingerprintPersistence(firstStore)).getOrCreate()
            firstJob.cancelAndJoin()

            // The second DataStore rides backgroundScope — cancelled automatically at test end.
            val secondStore = PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
                prefsFile.toOkioPath()
            }
            val readBack = DeviceIdentityStore(DataStoreFingerprintPersistence(secondStore)).getOrCreate()

            assertEquals(firstFingerprint, readBack, "fingerprint must survive a DataStore close/reopen cycle")
        }
}
