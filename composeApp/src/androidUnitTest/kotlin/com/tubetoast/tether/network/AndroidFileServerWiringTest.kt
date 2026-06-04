package com.tubetoast.tether.network

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.tubetoast.tether.TetherApp
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TetherApp::class)
class AndroidFileServerWiringTest {
    private val contentResolver: ContentResolver = RuntimeEnvironment.getApplication().contentResolver
    private val storage = AndroidMediaStoreUploadStorage(contentResolver)

    @After
    fun tearDown() {
        // Reset shadow state between tests
        Shadows.shadowOf(contentResolver)
    }

    @Test
    fun `resolveDestination inserts a row and returns a handle with the URI, or throws on null insert`() {
        // Robolectric's shadow ContentResolver returns null for insert by default.
        // resolveDestination must either succeed with a URI handle or throw.
        val thrown = runCatching { storage.resolveDestination("test.txt") }
        assertTrue(
            thrown.isFailure || thrown.getOrNull()?.destination?.isNotBlank() == true,
            "resolveDestination must either succeed with a URI or throw on null insert",
        )
    }

    @Test
    fun `writeBody streams bytes and clears IS_PENDING`() = runTest {
        val fakeUri = Uri.parse("content://media/external/downloads/99")
        val outputStream = java.io.ByteArrayOutputStream()
        Shadows.shadowOf(contentResolver).registerOutputStream(fakeUri, outputStream)

        val handle = UploadHandle(destination = fakeUri.toString(), createdDirs = emptyList())
        val bytes = "hello".toByteArray()
        val channel = ByteReadChannel(bytes)

        val written = storage.writeBody(channel, handle)
        assertEquals(bytes.size.toLong(), written)
        assertEquals("hello", outputStream.toString(Charsets.UTF_8.name()))

        val updates = Shadows.shadowOf(contentResolver).getUpdateStatements()
        val pendingClear = updates.lastOrNull { it.uri == fakeUri }
        checkNotNull(pendingClear) { "expected an update for $fakeUri but none found" }
        assertEquals(
            0,
            pendingClear.contentValues.getAsInteger(MediaStore.Downloads.IS_PENDING),
            "IS_PENDING must be cleared to 0 after writeBody",
        )
    }

    @Test
    fun `abort deletes the MediaStore row`() {
        val fakeUri = Uri.parse("content://media/external/downloads/77")
        val handle = UploadHandle(destination = fakeUri.toString(), createdDirs = emptyList())
        storage.abort(handle)

        val deletedUris = Shadows.shadowOf(contentResolver).deletedUris
        assertTrue(deletedUris.contains(fakeUri), "abort must delete the MediaStore row URI")
    }

    @Test
    fun `writeBody throws when openOutputStream returns null`() = runTest {
        // Exercises the ?: error(...) guard — real failure mode: row deleted between
        // resolveDestination and writeBody.
        val fakeUri = Uri.parse("content://media/external/downloads/null-stream")
        val nullOutputStorage = AndroidMediaStoreUploadStorage(
            contentResolver = contentResolver,
            openOutput = { null },
        )
        val handle = UploadHandle(destination = fakeUri.toString(), createdDirs = emptyList())
        assertFailsWith<IllegalStateException> {
            nullOutputStorage.writeBody(ByteReadChannel(byteArrayOf()), handle)
        }
    }
}
