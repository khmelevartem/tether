package com.tubetoast.tether.identity

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
    fun `fingerprint written by first DataStore is readable by second DataStore at same path`() = runTest {
        val job1 = SupervisorJob()
        val store1 = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(
                job1 + UnconfinedTestDispatcher(testScheduler),
            ),
        ) {
            prefsFile.toOkioPath()
        }
        val firstFingerprint = DeviceIdentityStore(DataStoreFingerprintPersistence(store1)).getOrCreate()
        // Join the cancelled scope so DataStore releases the file before reopening it — otherwise the
        // second factory call throws "multiple DataStores active for the same file".
        job1.cancelAndJoin()

        val job2 = SupervisorJob()
        val store2 = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(
                job2 + UnconfinedTestDispatcher(testScheduler),
            ),
        ) {
            prefsFile.toOkioPath()
        }
        val readBack = DeviceIdentityStore(DataStoreFingerprintPersistence(store2)).getOrCreate()
        job2.cancelAndJoin()

        assertEquals(firstFingerprint, readBack, "fingerprint must survive a DataStore close/reopen cycle")
    }
}
