package com.tubetoast.tether.network

import android.content.Context
import com.tubetoast.tether.TetherApp
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the Android FileServer wiring where AndroidMediaStoreUploadStorage and
 * DefaultTransferActivityTracker are composed together — mirroring AndroidAppContainer.
 *
 * SDK 28 forces the legacy file path in AndroidMediaStoreUploadStorage (pre-API-29 branch),
 * which Robolectric's ShadowEnvironment can honour without a real MediaStore.
 *
 * Coverage gap: the API 29+ `writeViaMediaStore` branch of AndroidMediaStoreUploadStorage —
 * the default production path on modern Android — is not exercised here. Robolectric's
 * ShadowContentResolver does not honour MediaStore.Downloads inserts reliably, making
 * end-to-end verification of that branch impossible in a unit test context. It is tracked
 * as a follow-up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TetherApp::class)
class AndroidFileServerWiringTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val keyDir = context.cacheDir.resolve("wiring-test-keys").apply { mkdirs() }
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
    private var server: FileServer? = null

    @After
    fun teardown() {
        client.close()
        server?.stop()
        keyDir.deleteRecursively()
    }

    @Test
    fun `upload fires tracker once and storage receives the body`() {
        var enters = 0
        var exits = 0
        val tracker = DefaultTransferActivityTracker(
            onFirstEnter = { enters++ },
            onLastExit = { exits++ },
        )
        val storage = CountingUploadStorage(AndroidMediaStoreUploadStorage(context))
        val srv = FileServer(
            port = 0,
            trustedDeviceStore = TrustedDeviceStore(context),
            deviceKeyPair = DeviceKeyPair(keyDir),
            storage = storage,
            tracker = tracker,
        )
        server = srv
        val port = srv.start()

        // runBlocking rather than runTest: CIO embeddedServer hardcodes Dispatchers.IOBridge
        // and cannot be pinned to a TestCoroutineScheduler.
        runBlocking {
            val response = client.post("http://localhost:$port/upload?name=wiring.txt") {
                contentType(ContentType.Application.OctetStream)
                setBody("hello wiring".toByteArray())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val savedPath = response.body<Map<String, String>>()["savedPath"]!!
            // On the legacy path (API < 29) the storage resolves a canonical file path.
            // Robolectric may not mount external storage, so we assert path plausibility
            // rather than reading back file content.
            assertTrue(savedPath.isNotBlank(), "savedPath must be non-blank")
        }

        assertEquals(1, enters, "tracker.onFirstEnter must fire exactly once")
        assertEquals(1, exits, "tracker.onLastExit must fire exactly once")
        assertEquals(1, storage.writeBodyCalls, "storage.writeBody must be called exactly once")
    }
}

private class CountingUploadStorage(
    private val delegate: UploadStorage,
) : UploadStorage by delegate {
    var writeBodyCalls = 0
        private set

    override suspend fun writeBody(body: io.ktor.utils.io.ByteReadChannel, destination: String): Long {
        writeBodyCalls++
        return delegate.writeBody(body, destination)
    }
}
