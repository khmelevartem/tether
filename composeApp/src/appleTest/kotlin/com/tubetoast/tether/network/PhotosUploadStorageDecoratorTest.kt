@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // --- toggle-off: Photos API must not be reached, delegate bytes returned, no delete ---

    @Test
    fun commit_with_toggle_off_skips_save_and_keeps_file() = runTest {
        val fakeStorage = FakeUploadStorage(bytesWritten = 42L)
        var saveToGalleryCalled = false
        val deletedPaths = mutableListOf<String>()
        val decorator = PhotosUploadStorageDecorator(
            delegate = fakeStorage,
            saveToGallery = {
                saveToGalleryCalled = true
                false
            },
            backgroundScope = this,
            mediaClassifier = { MediaType.Image },
            deleteFile = { deletedPaths.add(it) },
        )
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        val result = decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(42L, result)
        assertTrue(fakeStorage.writeBodyCalled)
        assertTrue(saveToGalleryCalled)
        assertTrue(deletedPaths.isEmpty())
    }

    // --- non-media: Photos API skipped, delegate still called, no delete ---

    @Test
    fun commit_with_non_media_file_skips_save_and_keeps_file() = runTest {
        val fakeStorage = FakeUploadStorage(bytesWritten = 7L)
        val deletedPaths = mutableListOf<String>()
        val decorator = PhotosUploadStorageDecorator(
            delegate = fakeStorage,
            saveToGallery = { true },
            backgroundScope = this,
            mediaClassifier = { null },
            deleteFile = { deletedPaths.add(it) },
        )
        val handle = UploadHandle(destination = "/tmp/doc.pdf", createdDirs = emptyList())

        val result = decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(7L, result)
        assertTrue(fakeStorage.writeBodyCalled)
        assertTrue(deletedPaths.isEmpty())
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

    // --- resolveAuthorization branches ---

    @Test
    fun authorized_status_triggers_save_and_deletes_source() = runTest {
        val fakeLibrary = FakePhotosLibrary(status = PhotosAuthStatus.Authorized, saveResult = true)
        val fakeStorage = FakeUploadStorage(bytesWritten = 1L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(1, fakeLibrary.saveCallCount)
        assertFalse(fakeLibrary.requestAuthCalled)
        assertEquals(listOf("/tmp/photo.jpg"), deletedPaths)
    }

    @Test
    fun limited_status_triggers_save_not_prompt_and_deletes_source() = runTest {
        val fakeLibrary = FakePhotosLibrary(status = PhotosAuthStatus.Limited, saveResult = true)
        val fakeStorage = FakeUploadStorage(bytesWritten = 1L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(1, fakeLibrary.saveCallCount)
        assertFalse(fakeLibrary.requestAuthCalled)
        assertEquals(listOf("/tmp/photo.jpg"), deletedPaths)
    }

    @Test
    fun denied_status_skips_save_and_prompt_no_delete() = runTest {
        val fakeLibrary = FakePhotosLibrary(status = PhotosAuthStatus.Denied)
        val fakeStorage = FakeUploadStorage(bytesWritten = 1L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(0, fakeLibrary.saveCallCount)
        assertFalse(fakeLibrary.requestAuthCalled)
        assertTrue(deletedPaths.isEmpty())
    }

    @Test
    fun restricted_status_skips_save_and_prompt_no_delete() = runTest {
        val fakeLibrary = FakePhotosLibrary(status = PhotosAuthStatus.Restricted)
        val fakeStorage = FakeUploadStorage(bytesWritten = 1L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(0, fakeLibrary.saveCallCount)
        assertFalse(fakeLibrary.requestAuthCalled)
        assertTrue(deletedPaths.isEmpty())
    }

    @Test
    fun not_determined_status_triggers_prompt_then_save_and_deletes_source_when_granted() = runTest {
        val fakeLibrary = FakePhotosLibrary(
            status = PhotosAuthStatus.NotDetermined,
            promptResult = true,
            saveResult = true,
        )
        val fakeStorage = FakeUploadStorage(bytesWritten = 1L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertTrue(fakeLibrary.requestAuthCalled)
        assertEquals(1, fakeLibrary.saveCallCount)
        assertEquals(listOf("/tmp/photo.jpg"), deletedPaths)
    }

    @Test
    fun not_determined_status_triggers_prompt_then_skips_save_and_no_delete_when_denied() = runTest {
        val fakeLibrary = FakePhotosLibrary(
            status = PhotosAuthStatus.NotDetermined,
            promptResult = false,
        )
        val fakeStorage = FakeUploadStorage(bytesWritten = 1L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertTrue(fakeLibrary.requestAuthCalled)
        assertEquals(0, fakeLibrary.saveCallCount)
        assertTrue(deletedPaths.isEmpty())
    }

    // --- file-always-remains invariant ---

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

    @Test
    fun delegate_bytes_preserved_when_save_fails() = runTest {
        val fakeLibrary = FakePhotosLibrary(
            status = PhotosAuthStatus.Authorized,
            saveResult = false,
        )
        val fakeStorage = FakeUploadStorage(bytesWritten = 999L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        val result = decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(999L, result)
        assertTrue(fakeStorage.writeBodyCalled)
        assertFalse(fakeStorage.abortCalled)
        assertEquals(1, fakeLibrary.saveCallCount)
        assertTrue(deletedPaths.isEmpty())
    }

    @Test
    fun delegate_bytes_preserved_when_save_throws() = runTest {
        var saveCallCount = 0
        val throwingLibrary = object : FakePhotosLibrary(status = PhotosAuthStatus.Authorized) {
            override suspend fun save(path: String, mediaType: MediaType): Boolean {
                saveCallCount++
                throw RuntimeException("codec unsupported")
            }
        }
        val fakeStorage = FakeUploadStorage(bytesWritten = 512L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, throwingLibrary, this, deletedPaths)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        val result = decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.commit(handle)
        advanceUntilIdle()

        assertEquals(512L, result)
        assertTrue(fakeStorage.writeBodyCalled)
        assertFalse(fakeStorage.abortCalled)
        assertEquals(1, saveCallCount)
        assertTrue(deletedPaths.isEmpty())
    }

    // --- regression: truncated upload must never reach Photos ---

    @Test
    fun aborted_upload_never_launches_photos_save() = runTest {
        val fakeLibrary = FakePhotosLibrary(status = PhotosAuthStatus.Authorized, saveResult = true)
        val fakeStorage = FakeUploadStorage(bytesWritten = 10L)
        val decorator = makeDecoratorWithLibrary(fakeStorage, fakeLibrary, this)
        val handle = UploadHandle(destination = "/tmp/photo.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle)
        decorator.abort(handle)
        advanceUntilIdle()

        assertTrue(fakeStorage.abortCalled)
        assertEquals(0, fakeLibrary.saveCallCount)
    }

    // --- de-dup: exactly one OS prompt for concurrent arrivals ---

    @Test
    fun concurrent_commits_with_not_determined_trigger_prompt_exactly_once() = runTest {
        // Two commits launch two background coroutines; the de-dup happens between them, not in
        // the pass-through writeBody. The prompt gate must suspend (via authGate.await()) so the
        // scheduler can interleave the second coroutine into `promptForAuthorization` while the
        // first is suspended inside `requestAddOnlyAuth`. Without this yield, virtual-time
        // coroutines run sequentially and both see a cleared `pendingAuth`.
        val authGate = CompletableDeferred<Boolean>()
        val suspendingLibrary = object : FakePhotosLibrary(
            status = PhotosAuthStatus.NotDetermined,
            saveResult = true,
        ) {
            override suspend fun requestAddOnlyAuth(): Boolean {
                super.requestAddOnlyAuth()
                return authGate.await()
            }
        }
        val fakeStorage = FakeUploadStorage(bytesWritten = 1L)
        val deletedPaths = mutableListOf<String>()
        val decorator = makeDecoratorWithLibrary(fakeStorage, suspendingLibrary, this, deletedPaths)
        val handle1 = UploadHandle(destination = "/tmp/photo1.jpg", createdDirs = emptyList())
        val handle2 = UploadHandle(destination = "/tmp/photo2.jpg", createdDirs = emptyList())

        decorator.writeBody(ByteReadChannel.Empty, handle1)
        decorator.writeBody(ByteReadChannel.Empty, handle2)
        decorator.commit(handle1)
        decorator.commit(handle2)
        // Advance until both background coroutines are suspended at authGate.await(), then release.
        advanceUntilIdle()
        authGate.complete(true)
        advanceUntilIdle()

        assertEquals(1, suspendingLibrary.requestAuthCallCount)
        assertEquals(2, suspendingLibrary.saveCallCount)
        assertEquals(2, deletedPaths.size)
    }

    // --- helpers ---

    // backgroundScope is never reached by callers of this helper (they don't call writeBody).
    // A CoroutineScope(SupervisorJob()) is explicit about not being a virtual-time scheduler,
    // which avoids the latent trap of a disconnected TestScope.
    private fun makeDecorator(
        delegate: FakeUploadStorage = FakeUploadStorage(),
        classifier: (String) -> MediaType? = { null },
    ): PhotosUploadStorageDecorator =
        PhotosUploadStorageDecorator(
            delegate = delegate,
            saveToGallery = { true },
            backgroundScope = CoroutineScope(SupervisorJob()),
            mediaClassifier = classifier,
            deleteFile = {},
        )

    private fun makeDecoratorWithLibrary(
        delegate: FakeUploadStorage,
        library: PhotosLibrary,
        scope: TestScope,
        deletedPaths: MutableList<String> = mutableListOf(),
    ): PhotosUploadStorageDecorator =
        PhotosUploadStorageDecorator(
            delegate = delegate,
            saveToGallery = { true },
            backgroundScope = scope,
            mediaClassifier = { MediaType.Image },
            photosLibrary = library,
            deleteFile = { deletedPaths.add(it) },
        )
}

private open class FakePhotosLibrary(
    private val status: PhotosAuthStatus,
    private val promptResult: Boolean = false,
    private val saveResult: Boolean = false,
) : PhotosLibrary {
    var requestAuthCalled = false
        private set
    var requestAuthCallCount = 0
        private set
    var saveCallCount = 0
        private set

    override fun addOnlyAuthStatus(): PhotosAuthStatus = status

    override suspend fun requestAddOnlyAuth(): Boolean {
        requestAuthCalled = true
        requestAuthCallCount++
        return promptResult
    }

    override suspend fun save(path: String, mediaType: MediaType): Boolean {
        saveCallCount++
        return saveResult
    }
}

private class FakeUploadStorage(
    private val bytesWritten: Long = 0L,
) : UploadStorage {
    var ensureRootCalled = false
    var resolveDestinationCalled = false
    var writeBodyCalled = false
    var commitCalled = false
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

    override fun commit(handle: UploadHandle) {
        commitCalled = true
    }

    override fun abort(handle: UploadHandle) {
        abortCalled = true
    }
}
