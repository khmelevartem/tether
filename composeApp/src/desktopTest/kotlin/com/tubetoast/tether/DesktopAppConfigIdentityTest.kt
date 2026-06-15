package com.tubetoast.tether

import com.tubetoast.tether.config.EphemeralDeviceNamePersistence
import com.tubetoast.tether.di.CliAppContainer
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.deviceIdFromPublicKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DesktopAppConfigIdentityTest {
    private lateinit var tmpDir: File

    @BeforeTest
    fun setup() {
        tmpDir = Files.createTempDirectory("tether-identity-test").toFile()
    }

    @AfterTest
    fun teardown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `configDir routes preferencesFilePath into the directory`() {
        val config = DefaultDesktopAppConfig(port = 0, configDir = tmpDir)
        assertEquals(
            File(tmpDir, "preferences.preferences_pb").absolutePath,
            config.preferencesFilePath,
        )
    }

    @Test
    fun `configDir leaves namePersistenceOverride null so container uses DataStore-backed persistence`() {
        val config = DefaultDesktopAppConfig(port = 0, configDir = tmpDir)
        assertNull(config.namePersistenceOverride)
    }

    @Test
    fun `persistent fingerprint is stable across independent containers sharing the same configDir`() {
        val fp1 = CliAppContainer(
            DefaultDesktopAppConfig(port = 0, configDir = tmpDir),
        ).deviceIdentityStore.fingerprint()
        val fp2 = CliAppContainer(
            DefaultDesktopAppConfig(port = 0, configDir = tmpDir),
        ).deviceIdentityStore.fingerprint()
        assertEquals(
            fp1,
            fp2,
            "EC key must reload deterministically; two containers on the same dir must agree on the fingerprint",
        )
    }

    @Test
    fun `persistent fingerprint equals direct derivation from the key in configDir`() {
        val container = CliAppContainer(DefaultDesktopAppConfig(port = 0, configDir = tmpDir))
        val fp = container.deviceIdentityStore.fingerprint()
        val expected = deviceIdFromPublicKey(DeviceKeyPair(tmpDir).publicKey)
        assertEquals(64, fp.length, "fingerprint must be 64 hex chars")
        assertEquals(expected, fp, "container fingerprint must equal direct derivation from the same key dir")
    }

    // nameStore reads/writes DataStore on real disk; runBlocking awaits that real I/O.
    @Suppress("ktlint:tether:no-run-blocking-in-tests")
    @Test
    fun `configDir wires DataStore-backed name persistence and round-trips a name`() = runBlocking {
        val container = CliAppContainer(DefaultDesktopAppConfig(port = 0, configDir = tmpDir))
        container.nameStore.init()
        container.nameStore.setName("PersistMe")
        assertEquals("PersistMe", container.nameStore.name.first())
    }

    @Test
    fun `ephemeral identity differs between containers with different key dirs`() {
        fun ephemeralConfig(dir: File) = DefaultDesktopAppConfig(
            port = 0,
            configDir = dir,
            namePersistenceOverride = EphemeralDeviceNamePersistence(),
        )
        val dir1 = Files.createTempDirectory("tether-ephemeral-1").toFile()
        val dir2 = Files.createTempDirectory("tether-ephemeral-2").toFile()
        try {
            val fp1 = CliAppContainer(ephemeralConfig(dir1)).deviceIdentityStore.fingerprint()
            val fp2 = CliAppContainer(ephemeralConfig(dir2)).deviceIdentityStore.fingerprint()
            assertNotEquals(fp1, fp2, "different key dirs must yield different fingerprints")
        } finally {
            dir1.deleteRecursively()
            dir2.deleteRecursively()
        }
    }

    @Test
    fun `ephemeral identity is stable for same key dir`() {
        fun ephemeralConfig() = DefaultDesktopAppConfig(
            port = 0,
            deviceKeyPair = DeviceKeyPair(tmpDir),
            namePersistenceOverride = EphemeralDeviceNamePersistence(),
        )
        val fp1 = CliAppContainer(ephemeralConfig()).deviceIdentityStore.fingerprint()
        val fp2 = CliAppContainer(ephemeralConfig()).deviceIdentityStore.fingerprint()
        assertEquals(fp1, fp2, "same key dir must yield same fingerprint")
    }
}
