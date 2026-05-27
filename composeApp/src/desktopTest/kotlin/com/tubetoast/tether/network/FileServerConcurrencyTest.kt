package com.tubetoast.tether.network

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class FileServerConcurrencyTest {
    private val cleanupPaths = mutableListOf<File>()
    private var startedServer: FileServer? = null

    @AfterTest
    fun teardown() {
        startedServer?.stop()
        startedServer = null
        cleanupPaths.forEach { it.deleteRecursively() }
        cleanupPaths.clear()
    }

    private fun newServer(downloadsDir: File): FileServer {
        val configDir = Files.createTempDirectory("tether-concurrency-test-keys").toFile().also(cleanupPaths::add)
        val server = FileServer(
            port = 0,
            downloadsDir = downloadsDir,
            trustedDeviceStore = TrustedDeviceStore(configDir),
            deviceKeyPair = DeviceKeyPair(configDir),
        )
        startedServer = server
        return server
    }

    @Test
    fun `concurrent same-name uploads produce distinct files with correct payloads`() {
        val n = 8
        val tmpDir = Files.createTempDirectory("tether-concurrent-dedup").toFile().also(cleanupPaths::add)
        val server = newServer(tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            val payloads = (0 until n).map { i -> "payload-$i".toByteArray() }
            val paths = runBlocking {
                payloads
                    .map { payload ->
                        async {
                            val response = client.post("http://localhost:$port/upload?name=photo.jpg") {
                                contentType(ContentType.Application.OctetStream)
                                setBody(payload)
                            }
                            assertEquals(HttpStatusCode.OK, response.status, "upload must succeed")
                            response.body<Map<String, String>>()["savedPath"]!!
                        }
                    }.awaitAll()
            }

            assertEquals(n, paths.distinct().size, "all $n uploads must land at distinct paths")
            val files = tmpDir.listFiles() ?: emptyArray()
            assertEquals(n, files.size, "exactly $n files must exist in downloads dir")
            val savedContents = paths.map { File(it).readText() }.toSet()
            val expectedContents = payloads.map { it.decodeToString() }.toSet()
            assertEquals(expectedContents, savedContents, "all payloads must be saved intact")
        } finally {
            client.close()
        }
    }

    @Test
    fun `abort of one upload does not delete dir shared with concurrent sibling`() {
        val tmpDir = Files.createTempDirectory("tether-concurrent-abort").toFile().also(cleanupPaths::add)
        val server = newServer(tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val successDeferred = async {
                    client.post("http://localhost:$port/upload?name=subdir/B.bin") {
                        contentType(ContentType.Application.OctetStream)
                        setBody("complete-payload".toByteArray())
                    }
                }

                // Abort A by client-disconnect mid-stream.
                try {
                    withTimeout(150.milliseconds) {
                        client.post("http://localhost:$port/upload?name=subdir/A.bin") {
                            setBody(SlowAbortContent(totalBytes = 8L * 1024 * 1024))
                        }
                    }
                    fail("expected cancellation, request unexpectedly completed")
                } catch (_: TimeoutCancellationException) {
                } catch (_: Exception) {
                }

                val successResponse = successDeferred.await()
                assertEquals(HttpStatusCode.OK, successResponse.status, "B upload must complete")
                val savedPath = successResponse.body<Map<String, String>>()["savedPath"]!!

                withTimeout(5.seconds) {
                    while (File(tmpDir, "subdir/A.bin").exists()) delay(50.milliseconds)
                }

                assertTrue(File(savedPath).exists(), "B.bin must exist after A aborted")
                assertEquals("complete-payload", File(savedPath).readText())
                assertTrue(File(tmpDir, "subdir").exists(), "subdir must survive A's abort")
                assertFalse(File(tmpDir, "subdir/A.bin").exists(), "A.bin partial must be removed")
            }
        } finally {
            client.close()
        }
    }
}
