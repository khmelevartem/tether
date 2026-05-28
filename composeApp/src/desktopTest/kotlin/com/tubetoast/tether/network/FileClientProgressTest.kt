package com.tubetoast.tether.network

import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class FileClientProgressTest {
    private lateinit var tmpDir: File
    private lateinit var configDir: File
    private lateinit var tempStore: TempDataStore
    private lateinit var server: FileServer
    private lateinit var client: FileClient
    private lateinit var device: Device

    @BeforeTest
    fun setup() {
        tmpDir = Files.createTempDirectory("tether-progress-test").toFile()
        configDir = Files.createTempDirectory("tether-progress-test-keys").toFile()
        tempStore = TempDataStore()
        server = FileServer(
            port = 0,
            downloadsDir = tmpDir,
            trustedDeviceStore = DefaultTrustedDeviceStore(tempStore.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
        )
        val port = server.start()
        client = FileClient.default()
        device = Device(name = "test", host = "127.0.0.1", port = port)
    }

    @AfterTest
    fun teardown() {
        client.close()
        server.stop()
        tempStore.tearDown()
        tmpDir.deleteRecursively()
        configDir.deleteRecursively()
    }

    @Test
    fun `send with onProgress reports monotonically increasing bytes`() {
        val content = ByteArray(64 * 1024) { it.toByte() }
        val file = Files.createTempFile("progress-test", ".bin")
        file.writeBytes(content)

        val reports = mutableListOf<Long>()
        runBlocking {
            val result = client.send(device, file) { transferred, _ -> reports.add(transferred) }
            assertIs<SendResult.Success>(result)
        }

        assertTrue(reports.isNotEmpty(), "onProgress was never called")
        assertTrue(reports.last() == content.size.toLong(), "Last report must equal file size")
        assertTrue(reports.zipWithNext().all { (a, b) -> b >= a }, "Progress must be non-decreasing")

        Files.deleteIfExists(file)
    }

    @Test
    fun `send with onProgress preserves file content`() {
        val content = ByteArray(256) { it.toByte() }
        val file = Files.createTempFile("progress-content-test", ".bin")
        file.writeBytes(content)

        runBlocking {
            val result = client.send(device, file) { _, _ -> } as SendResult.Success
            val saved = File(result.savedPath).readBytes()
            assertTrue(content.contentEquals(saved), "Saved content must match sent content")
        }

        Files.deleteIfExists(file)
    }

    @Test
    fun `send without onProgress still works`() {
        val file = Files.createTempFile("no-progress-test", ".txt")
        file.writeBytes("hello".toByteArray())

        runBlocking {
            val result = client.send(device, file)
            assertIs<SendResult.Success>(result)
        }

        Files.deleteIfExists(file)
    }
}
