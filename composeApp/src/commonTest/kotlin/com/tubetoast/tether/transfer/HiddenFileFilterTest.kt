package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenFileFilterTest {
    private fun source(name: String): FileSource = object : FileSource {
        override val name = name
        override val relativePath = name
        override val sizeBytes: Long? = 0L

        override suspend fun openReadChannel(): ByteReadChannel = ByteReadChannel(byteArrayOf())

        override fun close() = Unit
    }

    @Test
    fun `DS_Store is hidden`() = assertFalse(HiddenFileFilter.isVisible(source(".DS_Store")))

    @Test
    fun `Thumbs_db is hidden`() = assertFalse(HiddenFileFilter.isVisible(source("Thumbs.db")))

    @Test
    fun `desktop_ini is hidden`() = assertFalse(HiddenFileFilter.isVisible(source("desktop.ini")))

    @Test
    fun `dotfile is hidden`() = assertFalse(HiddenFileFilter.isVisible(source(".dotfile")))

    @Test
    fun `regular document is visible`() = assertTrue(HiddenFileFilter.isVisible(source("Document.txt")))

    @Test
    fun `dot is hidden`() = assertFalse(HiddenFileFilter.isVisible(source(".")))

    @Test
    fun `dotdot is hidden`() = assertFalse(HiddenFileFilter.isVisible(source("..")))
}
