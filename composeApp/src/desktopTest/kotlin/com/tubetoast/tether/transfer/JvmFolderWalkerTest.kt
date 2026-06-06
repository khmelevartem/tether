package com.tubetoast.tether.transfer

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmFolderWalkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `nested structure produces POSIX-separated relative paths`() {
        val root = tmp.newFolder("Photos")
        root.resolve("a.jpg").createNewFile()
        val sub = root.resolve("sub").also { it.mkdir() }
        sub.resolve("b.jpg").createNewFile()

        val paths = JvmFolderWalker().walk(root).map { it.relativePath }.toSet()

        assertEquals(setOf("Photos/a.jpg", "Photos/sub/b.jpg"), paths)
    }

    @Test
    fun `hidden files are excluded`() {
        val root = tmp.newFolder("docs")
        root.resolve(".DS_Store").createNewFile()
        root.resolve(".hidden").createNewFile()
        root.resolve("Thumbs.db").createNewFile()
        root.resolve("visible.txt").createNewFile()

        val sources = JvmFolderWalker().walk(root)

        val names = sources.map { it.name }.toSet()
        assertTrue("visible.txt" in names)
        assertFalse(".DS_Store" in names)
        assertFalse(".hidden" in names)
        assertFalse("Thumbs.db" in names)
    }

    @Test
    fun `symlink cycle terminates and each real file appears exactly once`() {
        val root = tmp.newFolder("cycle_root")
        root.resolve("file.txt").createNewFile()
        val linkTarget = root.toPath()
        val link = root.resolve("loop").toPath()
        try {
            Files.createSymbolicLink(link, linkTarget)
        } catch (_: Exception) {
            // Symlink creation not supported on this filesystem — skip.
            return
        }

        val sources = JvmFolderWalker().walk(root)

        val names = sources.map { it.name }
        assertEquals(1, names.count { it == "file.txt" }, "file.txt must appear exactly once")
    }

    @Test
    fun `single file name equals relativePath when walking flat root`() {
        val root = tmp.newFolder("flat")
        root.resolve("note.txt").createNewFile()

        val sources = JvmFolderWalker().walk(root)

        assertEquals(1, sources.size)
        assertEquals("flat/note.txt", sources[0].relativePath)
        assertEquals("note.txt", sources[0].name)
    }

    @Test
    fun `empty folder returns empty list`() {
        val root = tmp.newFolder("empty")

        val sources = JvmFolderWalker().walk(root)

        assertEquals(emptyList(), sources)
    }

    @Test
    fun `broken symlink is swallowed and real sibling files are returned`() {
        val root = tmp.newFolder("broken_sym_root")
        root.resolve("real.txt").createNewFile()
        val link = root.resolve("dead_link").toPath()
        val nonExistent = root.toPath().resolve("does_not_exist")
        try {
            Files.createSymbolicLink(link, nonExistent)
        } catch (_: Exception) {
            // Symlink creation not supported on this filesystem — skip.
            return
        }

        val sources = JvmFolderWalker().walk(root)

        val names = sources.map { it.name }
        assertEquals(listOf("real.txt"), names)
    }
}
