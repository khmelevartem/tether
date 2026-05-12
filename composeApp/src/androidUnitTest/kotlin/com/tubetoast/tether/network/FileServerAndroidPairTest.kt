package com.tubetoast.tether.network

import android.content.Context
import com.tubetoast.tether.TetherApp
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

    @Test
    fun `pair returns 500 when Android store fails to persist`() {
        val throwingStore = object : TrustedDeviceStore(context) {
            override fun saveTrustedKey(deviceId: String, publicKey: ByteArray): Unit = throw IllegalStateException(
                "simulated DataStore write failure",
            )
        }
        val srv = FileServer(
            port = 0,
            downloadsDir = tmpDir,
            trustedDeviceStore = throwingStore,
            deviceKeyPair = DeviceKeyPair(keyPairDir),
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
