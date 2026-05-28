package com.tubetoast.tether.security

import com.tubetoast.tether.preferences.TempDataStore
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustedDeviceStoreTest {
    private lateinit var temp: TempDataStore
    private lateinit var store: DefaultTrustedDeviceStore

    @BeforeTest
    fun setup() {
        temp = TempDataStore()
        store = DefaultTrustedDeviceStore(temp.dataStore)
    }

    @AfterTest
    fun tearDown() {
        temp.tearDown()
    }

    @Test
    fun `isTrusted returns false for unknown device`() = runTest {
        assertFalse(store.isTrusted("unknown-device"))
    }

    @Test
    fun `isTrusted returns true after saving key`() = runTest {
        store.saveTrustedKey("trusted-peer", byteArrayOf(7, 8, 9))
        assertTrue(store.isTrusted("trusted-peer"))
    }

    @Test
    fun `getPublicKey returns null for unknown device`() = runTest {
        assertNull(store.getPublicKey("no-such-device"))
    }

    @Test
    fun `saveTrustedKey persists and getPublicKey returns same key`() = runTest {
        val key = byteArrayOf(1, 2, 3, 42)
        store.saveTrustedKey("device-1", key)
        val loaded = store.getPublicKey("device-1")
        assertNotNull(loaded)
        assertTrue(loaded.contentEquals(key), "persisted key must match original")
    }

    @Test
    fun `overwriting key for same deviceId replaces it`() = runTest {
        val keyA = byteArrayOf(1, 2, 3)
        val keyB = byteArrayOf(4, 5, 6)
        store.saveTrustedKey("device-x", keyA)
        store.saveTrustedKey("device-x", keyB)
        val loaded = store.getPublicKey("device-x")
        assertNotNull(loaded)
        assertTrue(loaded.contentEquals(keyB), "key must be replaced with latest value")
    }

    @Test
    fun `negative byte values round-trip correctly`() = runTest {
        val key = byteArrayOf(-128, -1, 0, 1, 127)
        store.saveTrustedKey("device-neg", key)
        val loaded = store.getPublicKey("device-neg")
        assertNotNull(loaded)
        assertTrue(loaded.contentEquals(key), "negative byte values must survive serialization round-trip")
    }

    @Test
    fun `empty key array round-trips correctly`() = runTest {
        val key = ByteArray(0)
        store.saveTrustedKey("device-empty", key)
        val loaded = store.getPublicKey("device-empty")
        assertNotNull(loaded)
        assertEquals(0, loaded.size, "empty key must round-trip as empty array")
    }
}
