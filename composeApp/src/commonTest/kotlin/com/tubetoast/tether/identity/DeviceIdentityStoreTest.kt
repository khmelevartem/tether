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
        val fp = store.getOrCreate()
        assertNotNull(fp)
        assertEquals(32, fp.length, "fingerprint must be 32 hex chars (128-bit)")
        assertTrue(fp.all { it in '0'..'9' || it in 'a'..'f' }, "fingerprint must be lowercase hex: $fp")
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
        val fp1 = DeviceIdentityStore(temp.dataStore).getOrCreate()
        val fp2 = DeviceIdentityStore(temp.dataStore).getOrCreate()
        assertEquals(fp1, fp2, "fingerprint must survive re-instantiation with same dataStore")
    }
}
