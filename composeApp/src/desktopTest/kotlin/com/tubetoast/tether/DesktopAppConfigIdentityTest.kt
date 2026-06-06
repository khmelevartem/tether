package com.tubetoast.tether

import com.tubetoast.tether.config.EphemeralDeviceNamePersistence
import com.tubetoast.tether.di.CliAppContainer
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.identity.EphemeralFingerprintPersistence
import com.tubetoast.tether.security.DeviceKeyPair
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
import kotlin.test.assertTrue

// DataStore writes to real disk via real dispatcher — virtual-time coroutines cannot
// synchronize on real file I/O.
@Suppress("ktlint:tether:no-run-blocking-in-tests")
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
    fun `configDir leaves persistence overrides null so container uses DataStore-backed persistence`() {
        val config = DefaultDesktopAppConfig(port = 0, configDir = tmpDir)
        assertNull(config.namePersistenceOverride)
        assertNull(config.fingerprintPersistenceOverride)
    }

    @Test
    fun `persistent fingerprint is written to disk in configDir`() = runBlocking {
        val container = CliAppContainer(DefaultDesktopAppConfig(port = 0, configDir = tmpDir))
        container.deviceIdentityStore.getOrCreate()
        val prefsFile = File(tmpDir, "preferences.preferences_pb")
        assertTrue(prefsFile.exists(), "preferences file must exist after getOrCreate: ${prefsFile.absolutePath}")
    }

    @Test
    fun `persistent fingerprint is stable within the same container`() = runBlocking {
        val container = CliAppContainer(DefaultDesktopAppConfig(port = 0, configDir = tmpDir))
        val fp1 = container.deviceIdentityStore.getOrCreate()
        val fp2 = container.deviceIdentityStore.getOrCreate()
        assertEquals(fp1, fp2, "fingerprint must be idempotent on the same container")
    }

    @Test
    fun `configDir wires DataStore-backed name persistence and round-trips a name`() = runBlocking {
        val container = CliAppContainer(DefaultDesktopAppConfig(port = 0, configDir = tmpDir))
        container.nameStore.init()
        container.nameStore.setName("PersistMe")
        assertEquals("PersistMe", container.nameStore.name.first())
    }

    @Test
    fun `ephemeral identity differs between fresh containers`() = runBlocking {
        // deviceKeyPair is pinned to tmpDir so the ephemeral path does not touch the real ~/.config/tether.
        fun ephemeralConfig() = DefaultDesktopAppConfig(
            port = 0,
            deviceKeyPair = DeviceKeyPair(tmpDir),
            namePersistenceOverride = EphemeralDeviceNamePersistence(),
            fingerprintPersistenceOverride = EphemeralFingerprintPersistence(),
        )
        val fp1 = CliAppContainer(ephemeralConfig()).deviceIdentityStore.getOrCreate()
        val fp2 = CliAppContainer(ephemeralConfig()).deviceIdentityStore.getOrCreate()

        assertNotEquals(fp1, fp2, "ephemeral fingerprints must differ between container instances")
    }
}
