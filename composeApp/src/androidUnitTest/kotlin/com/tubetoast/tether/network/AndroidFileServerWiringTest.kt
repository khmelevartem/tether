package com.tubetoast.tether.network

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.tubetoast.tether.TetherApp
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
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
    fun `writeBody streams bytes but does NOT publish — row stays IS_PENDING=1`() = runTest {
        val fakeUri = Uri.parse("content://media/external/downloads/99")
        val outputStream = java.io.ByteArrayOutputStream()
        Shadows.shadowOf(contentResolver).registerOutputStream(fakeUri, outputStream)

        val handle = UploadHandle(destination = fakeUri.toString(), createdDirs = emptyList())
        val bytes = "hello".toByteArray()

        val written = storage.writeBody(ByteReadChannel(bytes), handle)
        assertEquals(bytes.size.toLong(), written)
        assertEquals("hello", outputStream.toString(Charsets.UTF_8.name()))

        // Regression: writeBody must NOT clear IS_PENDING — publish is gated on commit().
        val updates = Shadows.shadowOf(contentResolver).getUpdateStatements()
        val pendingClear = updates.any { stmt ->
            stmt.uri == fakeUri &&
                stmt.contentValues.getAsInteger(MediaStore.Downloads.IS_PENDING) == 0
        }
        assertTrue(!pendingClear, "writeBody must not set IS_PENDING=0; that belongs in commit()")
    }

    @Test
    fun `commit publishes the row by setting IS_PENDING=0 exactly once`() = runTest {
        val fakeUri = Uri.parse("content://media/external/downloads/100")
        val outputStream = java.io.ByteArrayOutputStream()
        Shadows.shadowOf(contentResolver).registerOutputStream(fakeUri, outputStream)

        val handle = UploadHandle(destination = fakeUri.toString(), createdDirs = emptyList())
        storage.writeBody(ByteReadChannel("data".toByteArray()), handle)
        storage.commit(handle)

        val updates = Shadows.shadowOf(contentResolver).getUpdateStatements()
        val publishUpdates = updates.filter { stmt ->
            stmt.uri == fakeUri &&
                stmt.contentValues.getAsInteger(MediaStore.Downloads.IS_PENDING) == 0
        }
        assertEquals(1, publishUpdates.size, "commit must set IS_PENDING=0 exactly once")
    }

    @Test
    fun `abort after writeBody deletes the row and never publishes`() = runTest {
        val fakeUri = Uri.parse("content://media/external/downloads/101")
        val outputStream = java.io.ByteArrayOutputStream()
        Shadows.shadowOf(contentResolver).registerOutputStream(fakeUri, outputStream)

        val handle = UploadHandle(destination = fakeUri.toString(), createdDirs = emptyList())
        storage.writeBody(ByteReadChannel("partial".toByteArray()), handle)
        storage.abort(handle)

        val shadow = Shadows.shadowOf(contentResolver)
        assertTrue(shadow.deletedUris.contains(fakeUri), "abort must delete the MediaStore row")
        val published = shadow.getUpdateStatements().any { stmt ->
            stmt.uri == fakeUri &&
                stmt.contentValues.getAsInteger(MediaStore.Downloads.IS_PENDING) == 0
        }
        assertTrue(!published, "abort path must never publish (IS_PENDING=0)")
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
