package com.tubetoast.tether.identity

import com.tubetoast.tether.preferences.TempDataStore
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceIdentityStoreTest {
    private val temp = TempDataStore()

    @AfterTest
    fun tearDown() = temp.tearDown()

    @Test
    fun `getOrCreate returns a 32-char hex string`() = runTest {
        val store = DeviceIdentityStore(temp.dataStore)
        val fingerprint = store.getOrCreate()
        assertNotNull(fingerprint)
        assertEquals(32, fingerprint.length, "fingerprint must be 32 hex chars (128-bit)")
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' }, "fingerprint must be lowercase hex: $fingerprint")
    }

    @Test
    fun `getOrCreate is idempotent across calls on same store`() = runTest {
        val store = DeviceIdentityStore(temp.dataStore)
        val first = store.getOrCreate()
        val second = store.getOrCreate()
        assertEquals(first, second)
    }

    @Test
    fun `getOrCreate persists across new store instances with same dataStore`() = runTest {
        val firstInstanceFingerprint = DeviceIdentityStore(temp.dataStore).getOrCreate()
        val secondInstanceFingerprint = DeviceIdentityStore(temp.dataStore).getOrCreate()
        assertEquals(firstInstanceFingerprint, secondInstanceFingerprint, "fingerprint must survive re-instantiation with same dataStore")
    }
}
