package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileClientTest {
    private val device
        get() = Device(id = "test@127.0.0.1:$serverPort", name = "test", host = "127.0.0.1", port = serverPort)

    private var serverPort = 0
    private var tmpDir = Files.createTempDirectory("tether-client-test").toFile()
    private val configDir = Files.createTempDirectory("tether-client-test-keys").toFile()
    private val server =
        FileServer(
            0,
            downloadsDir = tmpDir,
            trustedDeviceStore = TrustedDeviceStore(configDir),
            deviceKeyPair = DeviceKeyPair(configDir),
        )

    private fun setup(): FileClient {
        serverPort = server.start()
        return FileClient()
    }

    private fun teardown(client: FileClient) {
        client.close()
        server.stop()
        tmpDir.deleteRecursively()
        configDir.deleteRecursively()
    }

    @Test
    fun `send returns Success with saved path`() {
        val client = setup()
        try {
            val file = Files.createTempFile("send-test", ".txt")
            file.writeBytes("hello from client".toByteArray())
            runBlocking {
                val result = client.send(device, file)
                assertIs<SendResult.Success>(result)
                assertTrue(result.savedPath.endsWith(".txt"))
                assertEquals("hello from client", java.io.File(result.savedPath).readText())
            }
            Files.deleteIfExists(file)
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send throws FileNotFoundException for missing file`() {
        val client = setup()
        try {
            val missing = Files.createTempDirectory("gone").resolve("no-such-file.bin")
            runBlocking {
                assertFailsWith<FileNotFoundException> {
                    client.send(device, missing)
                }
            }
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send preserves file content exactly`() {
        val client = setup()
        try {
            val content = ByteArray(256) { it.toByte() }
            val file = Files.createTempFile("binary-test", ".bin")
            file.writeBytes(content)
            runBlocking {
                val result = client.send(device, file) as SendResult.Success
                val saved = java.io.File(result.savedPath).readBytes()
                assertTrue(content.contentEquals(saved), "Saved content does not match sent content")
            }
            Files.deleteIfExists(file)
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send returns Failure when server is not running`() {
        val client = FileClient()
        try {
            val file = Files.createTempFile("no-server", ".txt")
            file.writeBytes("data".toByteArray())
            val unreachable = Device(id = "x@127.0.0.1:1", name = "x", host = "127.0.0.1", port = 1)
            runBlocking {
                val result = client.send(unreachable, file)
                assertIs<SendResult.Failure>(result)
            }
            Files.deleteIfExists(file)
        } finally {
            client.close()
        }
    }
}
