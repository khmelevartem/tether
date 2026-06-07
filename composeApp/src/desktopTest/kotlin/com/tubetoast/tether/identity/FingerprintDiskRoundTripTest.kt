package com.tubetoast.tether.identity

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// DataStore writes to real disk via a real dispatcher — the store scope must be a real
// CoroutineScope (a virtual-time TestScope would never drive the read, deadlocking getOrCreate).
@Suppress("ktlint:tether:no-run-blocking-in-tests")
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
    fun `fingerprint written by first DataStore is readable by second DataStore at same path`() = runBlocking {
        val job1 = SupervisorJob()
        val store1 = PreferenceDataStoreFactory.createWithPath(scope = CoroutineScope(job1 + Dispatchers.IO)) {
            prefsFile.toOkioPath()
        }
        val firstFingerprint = DeviceIdentityStore(DataStoreFingerprintPersistence(store1)).getOrCreate()
        // Join the cancelled scope so DataStore flushes and releases the file before reopening it —
        // otherwise the second factory call throws "multiple DataStores active for the same file".
        job1.cancelAndJoin()

        val job2 = SupervisorJob()
        val store2 = PreferenceDataStoreFactory.createWithPath(scope = CoroutineScope(job2 + Dispatchers.IO)) {
            prefsFile.toOkioPath()
        }
        val readBack = DeviceIdentityStore(DataStoreFingerprintPersistence(store2)).getOrCreate()
        job2.cancelAndJoin()

        assertEquals(firstFingerprint, readBack, "fingerprint must survive a DataStore close/reopen cycle")
    }
}
