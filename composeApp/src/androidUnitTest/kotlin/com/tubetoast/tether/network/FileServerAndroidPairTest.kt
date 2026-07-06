package com.tubetoast.tether.network

import android.content.Context
import com.tubetoast.tether.TetherApp
import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.transfer.InboundCancelRegistry
import com.tubetoast.tether.transfer.InboundEventBus
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
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TetherApp::class)
class FileServerAndroidPairTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val tmpDir = context.cacheDir.resolve("android-pair-test").apply { mkdirs() }
    private val keyPairDir = context.cacheDir.resolve("android-pair-keys").apply { mkdirs() }
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
    private var server: FileServer? = null

    @After
    fun teardown() {
        client.close()
        server?.stop()
        tmpDir.deleteRecursively()
        keyPairDir.deleteRecursively()
    }

    // real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
    @Suppress("ktlint:tether:no-run-blocking-in-tests")
    @Test
    fun `pair returns 500 when Android store fails to persist`() {
        val throwingStore = object : TrustedDeviceStore {
            override suspend fun isTrusted(deviceId: String) = false

            override suspend fun saveTrustedKey(deviceId: String, publicKey: ByteArray) = throw IllegalStateException(
                "simulated DataStore write failure",
            )

            override suspend fun getPublicKey(deviceId: String): ByteArray? = null
        }
        val srv = FileServer(
            configuredPort = 0,
            uploadStorage = FileUploadStorage(
                root = tmpDir.absolutePath,
                backend = JvmUploadStorageBackend(tmpDir.absolutePath),
            ),
            trustedDeviceStore = throwingStore,
            deviceKeyPair = DeviceKeyPair(keyPairDir),
            inboundEventBus = InboundEventBus(),
            cancelRegistry = InboundCancelRegistry(),
        )
        server = srv
        val port = srv.start()
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = byteArrayOf(1, 2, 3), deviceName = "AndroidFailPeer"))
            }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }
}
