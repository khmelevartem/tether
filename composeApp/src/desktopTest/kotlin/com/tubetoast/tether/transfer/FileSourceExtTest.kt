package com.tubetoast.tether.transfer

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSourceExtTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `regular file produces single JvmPathFileSource`() {
        val file = tmp.newFile("note.txt")

        val sources = file.toFileSources()

        assertEquals(1, sources.size)
        assertEquals("note.txt", sources[0].name)
    }

    @Test
    fun `directory is walked and all files returned`() {
        val dir = tmp.newFolder("docs")
        dir.resolve("a.txt").createNewFile()
        dir.resolve("b.txt").createNewFile()

        val sources = dir.toFileSources()

        assertEquals(2, sources.size)
        val names = sources.map { it.name }.toSet()
        assertEquals(setOf("a.txt", "b.txt"), names)
    }

    @Test
    fun `neither file nor directory returns empty list`() {
        val nonExistent = tmp.root.resolve("ghost.txt")

        val sources = nonExistent.toFileSources()

        assertTrue(sources.isEmpty())
    }
}
