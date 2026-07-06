package com.tubetoast.tether.network

import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.transfer.InboundCancelRegistry
import com.tubetoast.tether.transfer.InboundEventBus
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class FileClientIntegrationTest {
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
            configuredPort = 0,
            uploadStorage = FileUploadStorage(
                root = tmpDir.absolutePath,
                backend = JvmUploadStorageBackend(tmpDir.absolutePath),
            ),
            trustedDeviceStore = DefaultTrustedDeviceStore(tempStore.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
            inboundEventBus = InboundEventBus(),
            cancelRegistry = InboundCancelRegistry(),
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
    fun `send preserves file content over real HTTP`() {
        val content = ByteArray(256) { it.toByte() }
        val file = Files.createTempFile("content-fidelity-test", ".bin")
        file.writeBytes(content)

        runBlocking {
            val result = client.send(device, file) as SendResult.Success
            val saved = File(result.savedPath).readBytes()
            assertTrue(content.contentEquals(saved), "Saved content must match sent content")
        }

        Files.deleteIfExists(file)
    }

    @Test
    fun `checkHealth returns true against a live server`() {
        runBlocking {
            assertTrue(client.checkHealth(device), "A live /health endpoint must report reachable")
        }
    }

    @Test
    fun `checkHealth returns false against a closed port`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val dead = Device(name = "dead", host = "127.0.0.1", port = closedPort)
        runBlocking {
            assertFalse(client.checkHealth(dead), "A refused connection must report unreachable")
        }
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
