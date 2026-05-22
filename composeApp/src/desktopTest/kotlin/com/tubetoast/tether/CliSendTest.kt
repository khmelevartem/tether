package com.tubetoast.tether

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class CliSendTest {
    private lateinit var tmpDir: File
    private lateinit var configDir: File
    private lateinit var server: FileServer
    private lateinit var client: FileClient
    private lateinit var device: Device

    @BeforeTest
    fun setup() {
        tmpDir = Files.createTempDirectory("tether-cli-test").toFile()
        configDir = Files.createTempDirectory("tether-cli-test-keys").toFile()
        server = FileServer(
            port = 0,
            downloadsDir = tmpDir,
            trustedDeviceStore = TrustedDeviceStore(configDir),
            deviceKeyPair = DeviceKeyPair(configDir),
        )
        val port = server.start()
        device = Device(name = "cli-test", host = "127.0.0.1", port = port)
        client = FileClient.default()
    }

    @AfterTest
    fun teardown() {
        client.close()
        server.stop()
        tmpDir.deleteRecursively()
        configDir.deleteRecursively()
    }

    @Test
    fun `send happy path transfers file and reports OK`() {
        val content = "hello from cli test".toByteArray()
        val file = Files.createTempFile("cli-send", ".txt")
        file.writeBytes(content)

        val messages = mutableListOf<String>()
        runBlocking {
            handleSend(
                client = client,
                peers = listOf(device),
                peerName = device.name,
                file = file,
                output = messages::add,
            )
        }

        val ok = messages.firstOrNull { it.startsWith("[send] OK") }
        assertTrue(ok != null, "Expected [send] OK but got: $messages")

        val savedPath = ok.substringAfter("→").trim()
        assertEquals(String(content), File(savedPath).readText())

        Files.deleteIfExists(file)
    }

    @Test
    fun `send reports error when peer not found`() {
        val file = Files.createTempFile("cli-no-peer", ".txt")
        file.writeBytes("data".toByteArray())

        val messages = mutableListOf<String>()
        runBlocking {
            handleSend(
                client = client,
                peers = emptyList(),
                peerName = "nonexistent",
                file = file,
                output = messages::add,
            )
        }

        assertTrue(
            messages.any { it.contains("peer 'nonexistent' not found") },
            "Expected peer-not-found error but got: $messages",
        )

        Files.deleteIfExists(file)
    }

    @Test
    fun `send warns and uses first when multiple peers share name`() {
        val content = "dup-peer test".toByteArray()
        val file = Files.createTempFile("cli-dup", ".txt")
        file.writeBytes(content)

        val duplicate = device.copy(host = "127.0.0.2")
        val errors = mutableListOf<String>()
        val messages = mutableListOf<String>()
        runBlocking {
            handleSend(
                client = client,
                peers = listOf(device, duplicate),
                peerName = device.name,
                file = file,
                output = messages::add,
                errorOutput = errors::add,
            )
        }

        assertTrue(errors.any { it.contains("multiple peers") }, "Expected WARN but got: $errors")
        assertTrue(messages.any { it.startsWith("[send] OK") }, "Expected OK but got: $messages")
        assertFalse(messages.any { it.startsWith("[send] FAIL") }, "Unexpected FAIL: $messages")

        Files.deleteIfExists(file)
    }

    @Test
    fun `send reports error when file does not exist`() {
        val nonExistent = Files.createTempDirectory("cli-missing").resolve("no-such-file.bin")
        val messages = mutableListOf<String>()
        runBlocking {
            handleSend(
                client = client,
                peers = listOf(device),
                peerName = device.name,
                file = nonExistent,
                output = messages::add,
            )
        }

        assertTrue(
            messages.any { it.contains("file not found") },
            "Expected file-not-found error but got: $messages",
        )
    }
}
