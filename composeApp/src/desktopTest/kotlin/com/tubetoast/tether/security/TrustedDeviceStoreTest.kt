package com.tubetoast.tether.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustedDeviceStoreTest {
    @Test
    fun `saveTrustedKey persists key across instances`() {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            val key = byteArrayOf(1, 2, 3, 42)
            TrustedDeviceStore(configDir).saveTrustedKey("device-1", key)

            val loaded = TrustedDeviceStore(configDir).getPublicKey("device-1")
            assertNotNull(loaded)
            assertTrue(loaded.contentEquals(key), "persisted key must match original")
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `isTrusted returns false for unknown device`() {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            assertFalse(TrustedDeviceStore(configDir).isTrusted("unknown-device"))
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `overwriting key for same deviceId replaces it`() {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            val store = TrustedDeviceStore(configDir)
            val keyA = byteArrayOf(1, 2, 3)
            val keyB = byteArrayOf(4, 5, 6)
            store.saveTrustedKey("device-x", keyA)
            store.saveTrustedKey("device-x", keyB)

            val loaded = store.getPublicKey("device-x")
            assertNotNull(loaded)
            assertTrue(loaded.contentEquals(keyB), "key must be replaced with latest value")
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `getPublicKey returns null for unknown device`() {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            assertNull(TrustedDeviceStore(configDir).getPublicKey("no-such-device"))
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `isTrusted returns true after saving key`() {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            val store = TrustedDeviceStore(configDir)
            store.saveTrustedKey("trusted-peer", byteArrayOf(7, 8, 9))
            assertTrue(store.isTrusted("trusted-peer"))
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `negative byte values round-trip correctly`() {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            val key = byteArrayOf(-128, -1, 0, 1, 127)
            TrustedDeviceStore(configDir).saveTrustedKey("device-neg", key)

            val loaded = TrustedDeviceStore(configDir).getPublicKey("device-neg")
            assertNotNull(loaded)
            assertTrue(loaded.contentEquals(key), "negative byte values must survive serialization round-trip")
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `50 parallel saveTrustedKey calls all persist atomically`() = runTest {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            val store = TrustedDeviceStore(configDir)
            val n = 50
            (0 until n)
                .map { i ->
                    async(Dispatchers.IO) {
                        store.saveTrustedKey(
                            "device-$i",
                            byteArrayOf(i.toByte(), (i + 1).toByte(), (i + 2).toByte()),
                        )
                    }
                }.awaitAll()
            val reloaded = TrustedDeviceStore(configDir)
            for (i in 0 until n) {
                val key = reloaded.getPublicKey("device-$i")
                assertNotNull(key, "device-$i must be present after concurrent writes")
                assertEquals(3, key.size)
                assertEquals(i.toByte(), key[0])
            }
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `empty key array round-trips correctly`() {
        val configDir = Files.createTempDirectory("tether-store-test").toFile()
        try {
            val key = ByteArray(0)
            TrustedDeviceStore(configDir).saveTrustedKey("device-empty", key)

            val loaded = TrustedDeviceStore(configDir).getPublicKey("device-empty")
            assertNotNull(loaded)
            assertTrue(loaded.isEmpty(), "empty key must round-trip as empty array")
        } finally {
            configDir.deleteRecursively()
        }
    }
}
