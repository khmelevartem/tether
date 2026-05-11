package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FileServerPairFailureTest {
    private lateinit var keyPairDir: File
    private lateinit var notADirectory: File
    private lateinit var server: FileServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeTest
    fun setup() {
        keyPairDir = Files.createTempDirectory("tether-pair-fail-keys").toFile()
        // Regular file in place of configDir → store's writeText throws, same path as a real disk failure.
        notADirectory = Files.createTempFile("tether-pair-fail", ".not-a-dir").toFile()
        val keyPair = DeviceKeyPair(keyPairDir)
        val throwingStore = TrustedDeviceStore(notADirectory)
        server = FileServer(port = 0, trustedDeviceStore = throwingStore, deviceKeyPair = keyPair)
        port = server.start()
        client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
    }

    @AfterTest
    fun teardown() {
        client.close()
        server.stop()
        keyPairDir.deleteRecursively()
        notADirectory.delete()
    }

    @Test
    fun `pair returns 500 when store fails to persist`() {
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = byteArrayOf(7, 7, 7), deviceName = "Whatever"))
            }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }
}
