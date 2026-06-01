@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.security

import com.tubetoast.tether.TempDirs
import com.tubetoast.tether.util.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.writeToFile
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceKeyPairTest {
    private val tempDirs = TempDirs(slug = "tether-keypair")

    @AfterTest
    fun cleanup() = tempDirs.cleanup()

    private fun newTempDir(): String = tempDirs.newDir()

    @Test
    fun publicKey_has_x509_p256_spki_shape() {
        val kp = DeviceKeyPair(configDir = newTempDir(), keychain = InMemoryKeychainStore())
        assertEquals(91, kp.publicKey.size)
        assertEquals(0x30.toByte(), kp.publicKey[0])
        assertEquals(0x59.toByte(), kp.publicKey[1])
        assertEquals(0x04.toByte(), kp.publicKey[26])
    }

    @Test
    fun publicKey_stable_across_instances() {
        val store = InMemoryKeychainStore()
        val dir = newTempDir()
        val first = DeviceKeyPair(configDir = dir, keychain = store)
        val second = DeviceKeyPair(configDir = dir, keychain = store)
        assertTrue(first.publicKey.contentEquals(second.publicKey))
    }

    @Test
    fun publicKey_inner_point_parses_as_p256_via_sec_key() {
        val kp = DeviceKeyPair(configDir = newTempDir(), keychain = InMemoryKeychainStore())
        val rawPoint = kp.publicKey.copyOfRange(26, 91)
        assertEquals(65, rawPoint.size)
        assertEquals(0x04.toByte(), rawPoint[0])
        val secKey: SecKeyRef? = parseP256PublicKey(rawPoint)
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

        DeviceKeyPair(configDir = dir, keychain = InMemoryKeychainStore())

        assertTrue(!fm.fileExistsAtPath(legacyPath), "legacy placeholder must be removed on init")
    }

    @Test
    fun keychain_unavailable_throws() {
        assertFailsWith<IllegalStateException> {
            DeviceKeyPair(configDir = newTempDir(), keychain = UnavailableKeychainStore())
        }
    }

    @Test
    fun corrupt_entry_triggers_regeneration_on_load() {
        val store = InMemoryKeychainStore()
        val dir = newTempDir()
        DeviceKeyPair(configDir = dir, keychain = store)

        val flaky = FlakyExtractKeychainStore(failCount = 1, delegate = store)
        val kp = DeviceKeyPair(configDir = dir, keychain = flaky)

        // The load-path corruption branch must run in this exact order:
        // find the existing key, fail extraction, delete the corrupt entry, then regenerate.
        // If the branch were removed and execution jumped straight to generate, the log would
        // start with "find:hit", "generate" instead of "find:hit", "extract:fail", "delete".
        assertEquals(
            listOf("find:hit", "extract:fail", "delete", "generate", "extract:ok"),
            flaky.callLog,
            "loadOrCreate must detect corruption before regenerating",
        )
        assertEquals(91, kp.publicKey.size, "a fresh key must be generated after corruption")
    }

    @Test
    fun extract_fails_twice_throws_illegal_state() {
        val alwaysFailing = FlakyExtractKeychainStore(failCount = Int.MAX_VALUE)
        assertFailsWith<IllegalStateException> {
            DeviceKeyPair(configDir = newTempDir(), keychain = alwaysFailing)
        }
    }
}

private fun parseP256PublicKey(rawPoint: ByteArray): SecKeyRef? {
    val nsData = rawPoint.toNSData() ?: return null
    val attrs = buildQuery {
        put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        put(kSecAttrKeyClass, kSecAttrKeyClassPublic)
        put(kSecAttrKeySizeInBits, NSNumber(int = 256))
    }
    return memScoped {
        val errorRef = alloc<CFErrorRefVar>()
        val cfData = CFBridgingRetain(nsData) as platform.CoreFoundation.CFDataRef
        val key = SecKeyCreateWithData(cfData, attrs, errorRef.ptr)
        CFRelease(cfData)
        CFRelease(attrs)
        if (key == null) errorRef.value?.let { CFRelease(it) }
        key
    }
}
