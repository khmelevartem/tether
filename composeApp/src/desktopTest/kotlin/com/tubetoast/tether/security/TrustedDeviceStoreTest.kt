package com.tubetoast.tether.security

import java.nio.file.Files
import kotlin.test.Test
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
}
