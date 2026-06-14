package com.tubetoast.tether.identity

import com.tubetoast.tether.security.DeviceKeyPair
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FingerprintDiskRoundTripTest {
    private lateinit var keyDir: java.io.File
    private lateinit var otherKeyDir: java.io.File

    @BeforeTest
    fun setup() {
        keyDir = Files.createTempDirectory("tether-fp-roundtrip").toFile()
        otherKeyDir = Files.createTempDirectory("tether-fp-roundtrip-other").toFile()
    }

    @AfterTest
    fun teardown() {
        keyDir.deleteRecursively()
        otherKeyDir.deleteRecursively()
    }

    @Test
    fun `two DeviceKeyPair instances from same dir yield equal fingerprint`() {
        val fp1 = DeviceIdentityStore(DeviceKeyPair(keyDir).publicKey).fingerprint()
        val fp2 = DeviceIdentityStore(DeviceKeyPair(keyDir).publicKey).fingerprint()
        assertEquals(fp1, fp2, "same key dir must yield same fingerprint")
    }

    @Test
    fun `DeviceKeyPair from different dir yields different fingerprint`() {
        val fp1 = DeviceIdentityStore(DeviceKeyPair(keyDir).publicKey).fingerprint()
        val fp2 = DeviceIdentityStore(DeviceKeyPair(otherKeyDir).publicKey).fingerprint()
        assertNotEquals(fp1, fp2, "different key dirs must yield different fingerprints")
    }
}
