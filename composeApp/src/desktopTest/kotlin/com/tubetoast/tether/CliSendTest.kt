package com.tubetoast.tether

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliSendTest {
    private val tmpDir = Files.createTempDirectory("tether-cli-test").toFile()
    private val configDir = Files.createTempDirectory("tether-cli-test-keys").toFile()
    private val server =
        FileServer(
            0,
            downloadsDir = tmpDir,
            trustedDeviceStore = TrustedDeviceStore(configDir),
            deviceKeyPair = DeviceKeyPair(configDir),
        )

    private fun setup(): Pair<FileClient, Device> {
        val port = server.start()
        val device = Device(id = "cli-test@127.0.0.1:$port", name = "cli-test", host = "127.0.0.1", port = port)
        return FileClient() to device
    }

    private fun teardown(client: FileClient) {
        client.close()
        server.stop()
        tmpDir.deleteRecursively()
        configDir.deleteRecursively()
    }

    @Test
    fun `send happy path transfers file and reports OK`() {
        val (client, device) = setup()
        try {
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
            assertEquals(String(content), java.io.File(savedPath).readText())

            Files.deleteIfExists(file)
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send reports error when peer not found`() {
        val (client, _) = setup()
        try {
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
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send warns and uses first when multiple peers share name`() {
        val (client, device) = setup()
        try {
            val content = "dup-peer test".toByteArray()
            val file = Files.createTempFile("cli-dup", ".txt")
            file.writeBytes(content)

            val duplicate = device.copy(id = "cli-test-2@127.0.0.1:${device.port}")
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
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send reports error when file does not exist`() {
        val (client, device) = setup()
        try {
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
        } finally {
            teardown(client)
        }
    }
}
