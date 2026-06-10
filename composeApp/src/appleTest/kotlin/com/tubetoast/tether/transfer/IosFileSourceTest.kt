@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tubetoast.tether.transfer

import com.tubetoast.tether.TempDirs
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
