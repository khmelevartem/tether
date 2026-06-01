@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.Security.SecKeyRef
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceKeyPairTest {
    private val tempDirs = mutableListOf<String>()

    @AfterTest
    fun cleanup() {
        val fm = NSFileManager.defaultManager
        tempDirs.forEach { fm.removeItemAtPath(it, error = null) }
        tempDirs.clear()
    }

    private fun newTempDir(): String {
        val path = "${NSTemporaryDirectory()}tether-keypair-${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        tempDirs += path
        return path
    }

    @Test
    fun publicKey_has_x509_p256_spki_shape() {
        val kp = DeviceKeyPair.withStore(newTempDir(), InMemoryKeychainStore())
        assertEquals(91, kp.publicKey.size)
        assertEquals(0x30.toByte(), kp.publicKey[0])
        assertEquals(0x59.toByte(), kp.publicKey[1])
        assertEquals(0x04.toByte(), kp.publicKey[26])
    }

    @Test
    fun publicKey_stable_across_instances() {
        val store = InMemoryKeychainStore()
        val dir = newTempDir()
        val first = DeviceKeyPair.withStore(dir, store)
        val second = DeviceKeyPair.withStore(dir, store)
        assertTrue(first.publicKey.contentEquals(second.publicKey))
    }

    @Test
    fun publicKey_inner_point_parses_as_p256_via_sec_key() {
        val kp = DeviceKeyPair.withStore(newTempDir(), InMemoryKeychainStore())
        val rawPoint = kp.publicKey.copyOfRange(26, 91)
        assertEquals(65, rawPoint.size)
        assertEquals(0x04.toByte(), rawPoint[0])
        val secKey: SecKeyRef? = Keychain("unused").publicKeyFromRawPoint(rawPoint)
        assertNotNull(secKey, "raw EC point must be accepted by SecKeyCreateWithData as a valid P-256 public key")
    }

    @Test
    fun legacy_placeholder_file_removed_on_init() {
        val dir = newTempDir()
        val legacyPath = "$dir/device_public.key"
        val fm = NSFileManager.defaultManager
        val dummyData = ByteArray(32) { it.toByte() }.toNSData()!!
        dummyData.writeToFile(legacyPath, atomically = true)
        assertTrue(fm.fileExistsAtPath(legacyPath), "pre-condition: legacy file must exist")

        DeviceKeyPair.withStore(dir, InMemoryKeychainStore())

        assertTrue(!fm.fileExistsAtPath(legacyPath), "legacy placeholder must be removed on init")
    }

    @Test
    fun keychain_unavailable_throws() {
        assertFailsWith<IllegalStateException> {
            DeviceKeyPair.withStore(newTempDir(), UnavailableKeychainStore())
        }
    }
}
