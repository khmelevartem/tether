package com.tubetoast.tether.identity

import com.tubetoast.tether.identity.DataStoreFingerprintPersistence
import com.tubetoast.tether.preferences.TempDataStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class CountingFingerprintPersistence : FingerprintPersistence {
    var writes = 0
    private var stored: String? = null

    override suspend fun read(): String? = stored

    override suspend fun write(value: String) {
        writes++
        stored = value
    }
}

class DeviceIdentityStoreTest {
    private val temp = TempDataStore()

    @AfterTest
    fun tearDown() = temp.tearDown()

    @Test
    fun `getOrCreate returns a 32-char hex string`() = runTest {
        val store = DeviceIdentityStore(DataStoreFingerprintPersistence(temp.dataStore))
        val fingerprint = store.getOrCreate()
        assertNotNull(fingerprint)
        assertEquals(32, fingerprint.length, "fingerprint must be 32 hex chars (128-bit)")
        assertTrue(
            fingerprint.all { it in '0'..'9' || it in 'a'..'f' },
            "fingerprint must be lowercase hex: $fingerprint",
        )
    }

    @Test
    fun `getOrCreate is idempotent across calls on same store`() = runTest {
        val store = DeviceIdentityStore(DataStoreFingerprintPersistence(temp.dataStore))
        val first = store.getOrCreate()
        val second = store.getOrCreate()
        assertEquals(first, second)
    }

    @Test
    fun `getOrCreate persists across new store instances with same dataStore`() = runTest {
        val firstInstanceFingerprint = DeviceIdentityStore(
            DataStoreFingerprintPersistence(temp.dataStore),
        ).getOrCreate()
        val secondInstanceFingerprint = DeviceIdentityStore(
            DataStoreFingerprintPersistence(temp.dataStore),
        ).getOrCreate()
        assertEquals(
            firstInstanceFingerprint,
            secondInstanceFingerprint,
            "fingerprint must survive re-instantiation with same dataStore",
        )
    }

    @Test
    fun `concurrent getOrCreate calls on a fresh store yield the same fingerprint and only one write`() =
        runTest {
            val writeCount = CountingFingerprintPersistence()
            val store = DeviceIdentityStore(writeCount)
            val results = mutableListOf<String>()
            coroutineScope {
                repeat(20) {
                    launch {
                        results += store.getOrCreate()
                    }
                }
            }
            assertEquals(1, writeCount.writes, "exactly one write must happen on concurrent first-call")
            assertTrue(results.all { it == results[0] }, "all concurrent calls must return the same fingerprint")
        }
}
