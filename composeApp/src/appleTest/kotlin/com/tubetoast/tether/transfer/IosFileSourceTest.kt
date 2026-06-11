@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.transfer

import com.tubetoast.tether.TempDirs
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosFileSourceTest {
    private val tempDirs = TempDirs(slug = "tether-filesource")

    @AfterTest
    fun cleanup() = tempDirs.cleanup()

    private fun writeTempFile(dir: String, name: String, bytes: ByteArray): String {
        val path = "$dir/$name"
        val file = fopen(path, "wb") ?: error("fopen failed for $path")
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
        }
        fclose(file)
        return path
    }

    @Test
    fun `sizeBytes returns file size`() {
        val dir = tempDirs.newDir()
        val bytes = ByteArray(1024) { it.toByte() }
        val path = writeTempFile(dir, "sized.bin", bytes)
        val url = NSURL.fileURLWithPath(path)
        val source = securityScopedFileSource(url, "sized.bin", sizeBytes = bytes.size.toLong())

        assertEquals(1024L, source.sizeBytes)
        source.close()
    }

    @Test
    fun `openReadChannel streams exact bytes for small file`() = runTest {
        val dir = tempDirs.newDir()
        val expected = ByteArray(256) { it.toByte() }
        val path = writeTempFile(dir, "small.bin", expected)
        val url = NSURL.fileURLWithPath(path)
        val source = securityScopedFileSource(url, "small.bin", sizeBytes = expected.size.toLong())

        val channel = source.openReadChannel()
        val actual = ByteArray(expected.size)
        var offset = 0
        while (offset < actual.size) {
            val n = channel.readAvailable(actual, offset, actual.size - offset)
            if (n <= 0) break
            offset += n
        }
        source.close()

        assertEquals(expected.size, offset)
        assertTrue(expected.contentEquals(actual))
    }

    @Test
    fun `openReadChannel streams exact bytes for multi-MB file`() = runTest {
        val dir = tempDirs.newDir()
        val size = 4 * 1024 * 1024 // 4 MB — exercises the read loop across multiple READ_BUFFER_SIZE chunks
        val expected = ByteArray(size) { (it % 251).toByte() }
        val path = writeTempFile(dir, "large.bin", expected)
        val url = NSURL.fileURLWithPath(path)
        val source = securityScopedFileSource(url, "large.bin", sizeBytes = size.toLong())

        val channel = source.openReadChannel()
        val actual = ByteArray(size)
        var offset = 0
        while (offset < actual.size) {
            val n = channel.readAvailable(actual, offset, actual.size - offset)
            if (n <= 0) break
            offset += n
        }
        source.close()

        assertEquals(size, offset)
        assertTrue(expected.contentEquals(actual))
    }

    @Test
    fun `openReadChannel throws UnreadableSourceException for missing file`() = runTest {
        val url = NSURL.fileURLWithPath("/nonexistent/path/file.bin")
        val source = securityScopedFileSource(url, "file.bin", sizeBytes = null)
        assertFailsWith<UnreadableSourceException> {
            source.openReadChannel()
        }
        source.close()
    }

    @Test
    fun `relativePath and name for folder entry`() {
        val url = NSURL.fileURLWithPath("/tmp/folder/sub/file.txt")
        val source = securityScopedFileSource(url, "folder/sub/file.txt", sizeBytes = null)
        assertEquals("folder/sub/file.txt", source.relativePath)
        assertEquals("file.txt", source.name)
        source.close()
    }

    @Test
    fun `lazy photo source re-materializes on each read so retry after close succeeds`() = runTest {
        val dir = tempDirs.newDir()
        val expected = ByteArray(2048) { (it % 251).toByte() }
        val path = writeTempFile(dir, "photo.txt", expected)
        val provider = NSItemProvider(contentsOfURL = NSURL.fileURLWithPath(path))
        val typeId = provider.registeredTypeIdentifiers.filterIsInstance<String>().firstOrNull()
            ?: error("no registered type identifiers")
        val source = LazyPhotoFileSource(provider, typeId, "photo.txt")

        assertNull(source.sizeBytes, "size is unknown before materialization")
        val first = source.drain(expected.size)
        assertEquals(expected.size.toLong(), source.sizeBytes, "size is known after materialization")
        source.close()
        // The retry path: a second open after close() must re-materialize a fresh temp and read.
        val second = source.drain(expected.size)
        source.close()

        assertTrue(expected.contentEquals(first), "first read must match the source bytes")
        assertTrue(expected.contentEquals(second), "retry read must match the source bytes")
        // loadFileRepresentation vends an OS-made copy, so moving it out must not consume the
        // provider's backing file — this is what makes the retry above re-readable, not luck.
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path), "source file must survive materialization")
    }

    @Test
    fun `lazy photo source surfaces load failure as UnreadableSourceException`() = runTest {
        val dir = tempDirs.newDir()
        val path = writeTempFile(dir, "photo.txt", ByteArray(8))
        val provider = NSItemProvider(contentsOfURL = NSURL.fileURLWithPath(path))
        // A type identifier the provider cannot vend → loadFileRepresentation calls back with an error.
        val source = LazyPhotoFileSource(provider, "public.nonexistent-type", "photo.txt")

        assertFailsWith<UnreadableSourceException> { source.openReadChannel() }
        source.close()
    }

    private suspend fun FileSource.drain(size: Int): ByteArray {
        val channel: ByteReadChannel = openReadChannel()
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val n = channel.readAvailable(buffer, offset, size - offset)
            if (n <= 0) break
            offset += n
        }
        return buffer.copyOf(offset)
    }

    @Test
    fun `tempCopyFileSource onClose deletes temp file`() = runTest {
        val dir = tempDirs.newDir()
        val bytes = ByteArray(16) { 0xAB.toByte() }
        val path = writeTempFile(dir, "temp.bin", bytes)
        val url = NSURL.fileURLWithPath(path)
        val fm = NSFileManager.defaultManager
        assertTrue(fm.fileExistsAtPath(path), "file must exist before close")

        val source = tempCopyFileSource(url, "temp.bin", sizeBytes = bytes.size.toLong())
        source.close()

        assertFalse(fm.fileExistsAtPath(path), "file must be deleted after close")
    }
}
