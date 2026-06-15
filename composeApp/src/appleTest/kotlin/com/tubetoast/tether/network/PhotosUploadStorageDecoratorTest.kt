@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotosUploadStorageDecoratorTest {
    // --- detectMediaType using an injected map-based classifier ---

    @Test
    fun detectMediaType_returns_Image_for_image_extension() {
        val decorator = makeDecorator(classifier = { ext ->
            when (ext) {
                "jpg", "png", "heic" -> MediaType.Image
                "mp4", "mov" -> MediaType.Video
                else -> null
            }
        })
        assertEquals(MediaType.Image, decorator.detectMediaType("/tmp/photo.jpg"))
    }

    @Test
    fun detectMediaType_returns_Video_for_video_extension() {
        val decorator = makeDecorator(classifier = { ext ->
            if (ext == "mp4") MediaType.Video else null
        })
        assertEquals(MediaType.Video, decorator.detectMediaType("/tmp/clip.mp4"))
    }

    @Test
    fun detectMediaType_returns_null_for_non_media_extension() {
        val decorator = makeDecorator(classifier = { null })
        assertNull(decorator.detectMediaType("/tmp/doc.pdf"))
    }

    @Test
    fun detectMediaType_returns_null_for_no_extension() {
        val decorator = makeDecorator(classifier = { MediaType.Image })
        // No extension produces empty string which detectMediaType treats as "no extension" → null
        assertNull(decorator.detectMediaType("/tmp/noextension"))
    }

    // --- toggle-off: Photos API must not be reached, delegate bytes returned ---

    @Test
    fun writeBody_with_toggle_off_delegates_and_returns_bytes() = runTest {
        val fakeStorage = FakeUploadStorage(bytesWritten = 42L)
        var saveToGalleryCalled = false
        val decorator = PhotosUploadStorageDecorator(
            delegate = fakeStorage,
            saveToGallery = {
                saveToGalleryCalled = true
                false
            },
            backgroundScope = this,
            mediaClassifier = { MediaType.Image },
        )
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        val result = decorator.writeBody(ByteReadChannel.Empty, handle)
        advanceUntilIdle()

        assertEquals(42L, result)
        assertTrue(fakeStorage.writeBodyCalled)
        assertTrue(saveToGalleryCalled)
    }

    // --- non-media: Photos API skipped, delegate still called ---

    @Test
    fun writeBody_with_non_media_file_delegates_and_returns_bytes() = runTest {
        val fakeStorage = FakeUploadStorage(bytesWritten = 7L)
        val decorator = PhotosUploadStorageDecorator(
            delegate = fakeStorage,
            saveToGallery = { true },
            backgroundScope = this,
            mediaClassifier = { null },
        )
        val handle = UploadHandle(destination = "/tmp/doc.pdf", createdDirs = emptyList())

        val result = decorator.writeBody(ByteReadChannel.Empty, handle)
        advanceUntilIdle()

        assertEquals(7L, result)
        assertTrue(fakeStorage.writeBodyCalled)
    }

    // --- delegate pass-through for resolveDestination and abort ---

    @Test
    fun resolveDestination_delegates() {
        val fakeStorage = FakeUploadStorage()
        val decorator = makeDecorator(fakeStorage)

        decorator.resolveDestination("some/path.jpg")

        assertTrue(fakeStorage.resolveDestinationCalled)
    }

    @Test
    fun abort_delegates() {
        val fakeStorage = FakeUploadStorage()
        val decorator = makeDecorator(fakeStorage)
        val handle = UploadHandle(destination = "/tmp/x", createdDirs = emptyList())

        decorator.abort(handle)

        assertTrue(fakeStorage.abortCalled)
    }

    @Test
    fun ensureRoot_delegates() {
        val fakeStorage = FakeUploadStorage()
        val decorator = makeDecorator(fakeStorage)

        decorator.ensureRoot()

        assertTrue(fakeStorage.ensureRootCalled)
    }

    // --- file-always-remains: writeBody result from delegate is forwarded ---

    @Test
    fun writeBody_returns_exactly_the_bytes_the_delegate_reports() = runTest {
        val fakeStorage = FakeUploadStorage(bytesWritten = 1234L)
        val decorator = PhotosUploadStorageDecorator(
            delegate = fakeStorage,
            saveToGallery = { true },
            backgroundScope = this,
            mediaClassifier = { MediaType.Image },
        )
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        val result = decorator.writeBody(ByteReadChannel.Empty, handle)

        assertEquals(1234L, result)
    }

    // --- helpers ---

    private fun makeDecorator(
        delegate: FakeUploadStorage = FakeUploadStorage(),
        classifier: (String) -> MediaType? = { null },
    ): PhotosUploadStorageDecorator =
        PhotosUploadStorageDecorator(
            delegate = delegate,
            saveToGallery = { true },
            backgroundScope = TestScope(),
            mediaClassifier = classifier,
        )
}

private class FakeUploadStorage(
    private val bytesWritten: Long = 0L,
) : UploadStorage {
    var ensureRootCalled = false
    var resolveDestinationCalled = false
    var writeBodyCalled = false
    var abortCalled = false

    override fun ensureRoot() {
        ensureRootCalled = true
    }

    override fun resolveDestination(relativePath: String): UploadHandle {
        resolveDestinationCalled = true
        return UploadHandle(destination = "/fake/$relativePath", createdDirs = emptyList())
    }

    override suspend fun writeBody(body: ByteReadChannel, handle: UploadHandle): Long {
        writeBodyCalled = true
        return bytesWritten
    }

    override fun abort(handle: UploadHandle) {
        abortCalled = true
    }
}
