@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import com.tubetoast.tether.TempDirs
import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.InMemoryKeychainStore
import com.tubetoast.tether.transfer.InboundCancelRegistry
import com.tubetoast.tether.transfer.InboundEventBus
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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
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
    private val tempDirs = TempDirs(slug = "tether-fs-concurrency")
    private val cleanupTempStores = mutableListOf<TempDataStore>()

    @AfterTest
    fun cleanup() {
        tempDirs.cleanup()
        cleanupTempStores.forEach { it.tearDown() }
        cleanupTempStores.clear()
    }

    private fun newTempDir(): String = tempDirs.newDir()

    private fun makeClient(): HttpClient = HttpClient(CIO) { install(ContentNegotiation) { json() } }

    private fun newTestServer(downloadsDir: String): FileServer {
        val temp = TempDataStore().also { cleanupTempStores += it }
        return FileServer(
            configuredPort = 0,
            uploadStorage = FileUploadStorage(
                root = downloadsDir,
                backend = AppleUploadStorageBackend(downloadsDir),
            ),
            trustedDeviceStore = DefaultTrustedDeviceStore(temp.dataStore),
            deviceKeyPair = DeviceKeyPair(keychain = InMemoryKeychainStore()),
            inboundEventBus = InboundEventBus(),
            cancelRegistry = InboundCancelRegistry(),
        )
    }

    @Test
    fun concurrent_same_name_uploads_produce_distinct_files_with_correct_payloads() {
        val n = 8
        val dir = newTempDir()
        val server = newTestServer(dir)
        val port = server.start()
        val client = makeClient()
        try {
            val payloads = (0 until n).map { i -> "payload-$i".encodeToByteArray() }
            val paths = runBlocking {
                payloads
                    .map { payload ->
                        async {
                            val response = client.post("http://localhost:$port/upload?name=photo.jpg") {
                                contentType(ContentType.Application.OctetStream)
                                setBody(payload)
                            }
                            assertEquals(HttpStatusCode.OK, response.status, "upload must succeed")
                            (response.body() as Map<String, String>)["savedPath"]!!
                        }
                    }.awaitAll()
            }

            assertEquals(n, paths.distinct().size, "all $n uploads must land at distinct paths")
            val files = NSFileManager.defaultManager
                .contentsOfDirectoryAtPath(dir, error = null) ?: emptyList<Any?>()
            val fileNames = files.filterIsInstance<String>()
            assertEquals(n, fileNames.size, "exactly $n files must exist in downloads dir")
            val savedContents = paths.map { readConcurrencyFileAsString(it) }.toSet()
            val expectedContents = payloads.map { it.decodeToString() }.toSet()
            assertEquals(expectedContents, savedContents, "all payloads must be saved intact")
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun abort_of_one_upload_does_not_delete_dir_shared_with_concurrent_sibling() {
        val dir = newTempDir()
        val server = newTestServer(dir)
        val port = server.start()
        val client = makeClient()
        try {
            runBlocking {
                val successDeferred = async {
                    client.post("http://localhost:$port/upload?name=subdir/B.bin") {
                        contentType(ContentType.Application.OctetStream)
                        setBody("complete-payload".encodeToByteArray())
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
                val savedPath = (successResponse.body() as Map<String, String>)["savedPath"]!!

                withTimeout(5.seconds) {
                    while (NSFileManager.defaultManager.fileExistsAtPath("$dir/subdir/A.bin")) {
                        delay(50.milliseconds)
                    }
                }

                assertTrue(
                    NSFileManager.defaultManager.fileExistsAtPath(savedPath),
                    "B.bin must exist after A aborted",
                )
                assertEquals("complete-payload", readConcurrencyFileAsString(savedPath))
                assertTrue(
                    NSFileManager.defaultManager.fileExistsAtPath("$dir/subdir"),
                    "subdir must survive A's abort",
                )
                assertFalse(
                    NSFileManager.defaultManager.fileExistsAtPath("$dir/subdir/A.bin"),
                    "A.bin partial must be removed",
                )
            }
        } finally {
            client.close()
            server.stop()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readConcurrencyFileAsString(path: String): String =
    NSString.stringWithContentsOfFile(
        path = path,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String
