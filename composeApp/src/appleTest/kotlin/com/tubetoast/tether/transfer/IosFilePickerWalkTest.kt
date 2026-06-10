@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tubetoast.tether.transfer

import com.tubetoast.tether.TempDirs
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosFilePickerWalkTest {
    private val tempDirs = TempDirs(slug = "tether-walk")

    @AfterTest
    fun cleanup() = tempDirs.cleanup()

    private fun createDir(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    private fun createFile(path: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) {
        val file = fopen(path, "wb") ?: error("fopen failed for $path")
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
        }
        fclose(file)
    }

    private fun createSymlink(src: String, dst: String) {
        NSFileManager.defaultManager.createSymbolicLinkAtPath(src, withDestinationPath = dst, error = null)
    }

    @Test
    fun `walkFolder yields correct relativePaths and excludes hidden files`() = runTest {
        val root = tempDirs.newDir()
        val folderName = "MyFolder"
        val folderPath = "$root/$folderName"

        createDir(folderPath)
        createDir("$folderPath/sub")
        createFile("$folderPath/a.txt")
        createFile("$folderPath/sub/b.txt")
        createFile("$folderPath/.hidden")
        createFile("$folderPath/.DS_Store")
        createFile("$folderPath/sub/c.dat")

        val folderUrl = NSURL.fileURLWithPath(folderPath)
        val picker = IosFilePicker(viewControllerProvider = { null })
        val sources = withContext(Dispatchers.IO) { picker.walkFolderInternal(folderUrl) }

        val paths = sources.map { it.relativePath }.toSet()

        assertTrue(paths.contains("$folderName/a.txt"), "must include a.txt: $paths")
        assertTrue(paths.contains("$folderName/sub/b.txt"), "must include sub/b.txt: $paths")
        assertTrue(paths.contains("$folderName/sub/c.dat"), "must include sub/c.dat: $paths")
        assertFalse(paths.contains("$folderName/.hidden"), "must exclude .hidden: $paths")
        assertFalse(paths.contains("$folderName/.DS_Store"), "must exclude .DS_Store: $paths")
    }

    @Test
    fun `walkFolder does not traverse symlinks`() = runTest {
        val root = tempDirs.newDir()
        val folderPath = "$root/WithSymlink"
        val externalDir = tempDirs.newDir()
        createDir(folderPath)
        createFile("$externalDir/external.txt")
        createSymlink("$folderPath/link", externalDir)

        val folderUrl = NSURL.fileURLWithPath(folderPath)
        val picker = IosFilePicker(viewControllerProvider = { null })
        val sources = withContext(Dispatchers.IO) { picker.walkFolderInternal(folderUrl) }

        val paths = sources.map { it.relativePath }.toSet()
        assertFalse(paths.any { it.contains("external.txt") }, "symlink target must not be traversed: $paths")
    }

    @Test
    fun `walkFolder excludes symlink to file`() = runTest {
        val root = tempDirs.newDir()
        val folderPath = "$root/WithFileSymlink"
        val externalDir = tempDirs.newDir()
        createDir(folderPath)
        createFile("$externalDir/target.txt")
        createFile("$folderPath/real.txt")
        // Symlink inside folder pointing to an external file — must be excluded.
        createSymlink("$folderPath/link-to-file.txt", "$externalDir/target.txt")

        val folderUrl = NSURL.fileURLWithPath(folderPath)
        val picker = IosFilePicker(viewControllerProvider = { null })
        val sources = withContext(Dispatchers.IO) { picker.walkFolderInternal(folderUrl) }

        val paths = sources.map { it.relativePath }.toSet()
        assertTrue(paths.contains("WithFileSymlink/real.txt"), "real file must be included: $paths")
        assertFalse(paths.any { it.contains("link-to-file.txt") }, "symlink to file must be excluded: $paths")
    }

    @Test
    fun `walkFolder returns correct sizeBytes for each file`() = runTest {
        val root = tempDirs.newDir()
        val folderPath = "$root/SizedFiles"
        createDir(folderPath)
        val smallBytes = ByteArray(42) { it.toByte() }
        val largeBytes = ByteArray(1024) { (it % 127).toByte() }
        createFile("$folderPath/small.bin", smallBytes)
        createFile("$folderPath/large.bin", largeBytes)

        val folderUrl = NSURL.fileURLWithPath(folderPath)
        val picker = IosFilePicker(viewControllerProvider = { null })
        val sources = withContext(Dispatchers.IO) { picker.walkFolderInternal(folderUrl) }

        val sizeMap = sources.associate { it.relativePath to it.sizeBytes }
        assertEquals(42L, sizeMap["SizedFiles/small.bin"], "small.bin size must match: $sizeMap")
        assertEquals(1024L, sizeMap["SizedFiles/large.bin"], "large.bin size must match: $sizeMap")
    }

    @Test
    fun `walkFolder on empty folder returns emptyList`() = runTest {
        val root = tempDirs.newDir()
        val folderPath = "$root/Empty"
        createDir(folderPath)

        val folderUrl = NSURL.fileURLWithPath(folderPath)
        val picker = IosFilePicker(viewControllerProvider = { null })
        val sources = withContext(Dispatchers.IO) { picker.walkFolderInternal(folderUrl) }

        assertTrue(sources.isEmpty(), "empty folder must yield empty list: $sources")
    }

    @Test
    fun `walkFolder on folder with only hidden files returns emptyList`() = runTest {
        val root = tempDirs.newDir()
        val folderPath = "$root/OnlyHidden"
        createDir(folderPath)
        createFile("$folderPath/.hidden")
        createFile("$folderPath/.DS_Store")

        val folderUrl = NSURL.fileURLWithPath(folderPath)
        val picker = IosFilePicker(viewControllerProvider = { null })
        val sources = withContext(Dispatchers.IO) { picker.walkFolderInternal(folderUrl) }

        assertTrue(sources.isEmpty(), "folder with only hidden files must yield empty list: $sources")
    }
}
