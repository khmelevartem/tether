package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileClientProgressTest {
    private val tmpDir = Files.createTempDirectory("tether-progress-test").toFile()
    private val server = FileServer(0, downloadsDir = tmpDir)

    private fun setup(): Pair<FileClient, Int> {
        val port = server.start()
        return FileClient() to port
    }

    private fun teardown(client: FileClient) {
        client.close()
        server.stop()
        tmpDir.deleteRecursively()
    }

    @Test
    fun `send with onProgress reports monotonically increasing bytes`() {
        val (client, port) = setup()
        try {
            val content = ByteArray(64 * 1024) { it.toByte() }
            val file = Files.createTempFile("progress-test", ".bin")
            file.writeBytes(content)
            val device = Device(id = "test@127.0.0.1:$port", name = "test", host = "127.0.0.1", port = port)

            val reports = mutableListOf<Long>()
            runBlocking {
                val result = client.send(device, file) { transferred, _ -> reports.add(transferred) }
                assertIs<SendResult.Success>(result)
            }

            assertTrue(reports.isNotEmpty(), "onProgress was never called")
            assertTrue(reports.last() == content.size.toLong(), "Last report must equal file size")
            assertTrue(reports.zipWithNext().all { (a, b) -> b >= a }, "Progress must be non-decreasing")

            Files.deleteIfExists(file)
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send with onProgress preserves file content`() {
        val (client, port) = setup()
        try {
            val content = ByteArray(256) { it.toByte() }
            val file = Files.createTempFile("progress-content-test", ".bin")
            file.writeBytes(content)
            val device = Device(id = "test@127.0.0.1:$port", name = "test", host = "127.0.0.1", port = port)

            runBlocking {
                val result = client.send(device, file) { _, _ -> } as SendResult.Success
                val saved = java.io.File(result.savedPath).readBytes()
                assertTrue(content.contentEquals(saved), "Saved content must match sent content")
            }

            Files.deleteIfExists(file)
        } finally {
            teardown(client)
        }
    }

    @Test
    fun `send without onProgress still works`() {
        val (client, port) = setup()
        try {
            val file = Files.createTempFile("no-progress-test", ".txt")
            file.writeBytes("hello".toByteArray())
            val device = Device(id = "test@127.0.0.1:$port", name = "test", host = "127.0.0.1", port = port)

            runBlocking {
                val result = client.send(device, file)
                assertIs<SendResult.Success>(result)
            }

            Files.deleteIfExists(file)
        } finally {
            teardown(client)
        }
    }
}
