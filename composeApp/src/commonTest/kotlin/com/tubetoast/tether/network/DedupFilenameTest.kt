package com.tubetoast.tether.network

import kotlin.test.Test
import kotlin.test.assertEquals

class DedupFilenameTest {
    private fun dedup(name: String, taken: Set<String>) =
        dedupFilename(name) { it in taken }

    @Test
    fun `non-colliding name returns unchanged`() {
        assertEquals("photo.jpg", dedup("photo.jpg", emptySet()))
    }

    @Test
    fun `colliding name with extension appends suffix before extension`() {
        assertEquals("photo_1.jpg", dedup("photo.jpg", setOf("photo.jpg")))
        assertEquals("photo_2.jpg", dedup("photo.jpg", setOf("photo.jpg", "photo_1.jpg")))
    }

    @Test
    fun `colliding name without extension appends suffix at end`() {
        assertEquals("README_1", dedup("README", setOf("README")))
    }

    @Test
    fun `dotfile collision appends suffix at end not before leading-dot name`() {
        assertEquals(".gitignore_1", dedup(".gitignore", setOf(".gitignore")))
        assertEquals(".env_2", dedup(".env", setOf(".env", ".env_1")))
    }

    @Test
    fun `dotfile with secondary extension treats trailing extension as the extension`() {
        assertEquals(".tar_1.gz", dedup(".tar.gz", setOf(".tar.gz")))
    }
}
