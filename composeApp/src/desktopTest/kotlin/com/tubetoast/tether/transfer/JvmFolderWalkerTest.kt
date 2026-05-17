package com.tubetoast.tether.transfer

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmFolderWalkerTest {
    @Test
    fun walksNestedFilesAndExcludesDotfiles() {
        val root = Files.createTempDirectory("walker-test")
        try {
            val sub = root.resolve("sub")
            Files.createDirectory(sub)
            Files.write(root.resolve("file1.txt"), byteArrayOf(1, 2, 3))
            Files.write(sub.resolve("file2.txt"), byteArrayOf(4, 5, 6))
            Files.write(root.resolve(".hidden"), byteArrayOf(7))
            Files.write(root.resolve("Thumbs.db"), byteArrayOf(8))

            val sources = walk(root)

            val names = sources.map { it.name }.toSet()
            assertTrue("file1.txt" in names)
            assertTrue("file2.txt" in names)
            assertTrue(".hidden" !in names, "Hidden files should be excluded")
            assertTrue("Thumbs.db" !in names, "System files should be excluded")

            val file2 = sources.first { it.name == "file2.txt" }
            assertEquals("sub/file2.txt", file2.relativePath)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun symlinkedCycleTerminatesAndIncludesFilesOnce() {
        val root = Files.createTempDirectory("walker-cycle")
        try {
            Files.write(root.resolve("real.txt"), byteArrayOf(1))
            val sub = root.resolve("sub")
            Files.createDirectory(sub)
            Files.createSymbolicLink(sub.resolve("back"), root)

            val sources = walk(root)

            val names = sources.map { it.name }
            assertEquals(1, names.count { it == "real.txt" }, "real.txt should appear exactly once")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun hiddenDirectoryIsSkipped() {
        val root = Files.createTempDirectory("walker-hidden-dir")
        try {
            val hiddenDir = root.resolve(".hidden-dir")
            Files.createDirectory(hiddenDir)
            Files.write(hiddenDir.resolve("inside.txt"), byteArrayOf(1))
            Files.write(root.resolve("visible.txt"), byteArrayOf(2))

            val sources = walk(root)

            val names = sources.map { it.name }
            assertTrue("visible.txt" in names)
            assertTrue("inside.txt" !in names, "Files inside hidden dirs should be excluded")
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
