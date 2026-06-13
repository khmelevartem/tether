@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.share

import com.tubetoast.tether.TempDirs
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPendingFilesReaderTest {
    private val tempDirs = TempDirs(slug = "tether-share-reader")
    private val fm = NSFileManager.defaultManager

    @AfterTest
    fun cleanup() = tempDirs.cleanup()

    private fun makeRoot(): String = tempDirs.newDir()

    private fun writeFile(dir: String, name: String, bytes: ByteArray): String {
        val path = "$dir/$name"
        val file = fopen(path, "wb") ?: error("fopen failed: $path")
        bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.toULong(), file) }
        fclose(file)
        return path
    }

    private fun makeDir(path: String) {
        fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    }

    private fun writeManifest(batchDir: String, entries: List<Pair<String, Long>>) {
        val json = entries.joinToString(",", prefix = "[", postfix = "]") { (name, size) ->
            """{"name":"$name","size":$size}"""
        }
        writeFile(batchDir, "manifest.json", json.encodeToByteArray())
    }

    private fun reader(root: String) = SharedPendingFilesReader(
        inboxDir = "$root/inbox",
        stagingDir = "$root/staging",
    )

    @Test
    fun `consume returns empty when inbox is empty`() = runTest {
        val root = makeRoot()
        val sources = reader(root).consume()
        assertTrue(sources.isEmpty())
    }

    @Test
    fun `consume wraps files from manifest and clears inbox batch`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-1"
        makeDir(inboxBatch)
        val content = "hello tether".encodeToByteArray()
        writeFile(inboxBatch, "hello.txt", content)
        writeManifest(inboxBatch, listOf("hello.txt" to content.size.toLong()))

        val sources = reader(root).consume()
        assertEquals(1, sources.size)
        assertEquals("hello.txt", sources[0].name)
        assertEquals(content.size.toLong(), sources[0].sizeBytes)

        // batch moved to staging, inbox entry gone
        assertFalse(fm.fileExistsAtPath(inboxBatch), "inbox batch must be gone after consume")
        assertTrue(fm.fileExistsAtPath("$root/staging/batch-1"), "staging batch must exist")

        // drain the source and close
        val channel = sources[0].openReadChannel()
        val buf = ByteArray(content.size)
        channel.readAvailable(buf)
        sources[0].close()

        assertEquals(content.decodeToString(), buf.decodeToString())
        // staging batch deleted after last source closed
        assertFalse(
            fm.fileExistsAtPath("$root/staging/batch-1"),
            "staging batch must be deleted after all sources closed",
        )
    }

    @Test
    fun `second consume returns empty after inbox cleared`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-2"
        makeDir(inboxBatch)
        val content = ByteArray(8) { it.toByte() }
        writeFile(inboxBatch, "data.bin", content)
        writeManifest(inboxBatch, listOf("data.bin" to content.size.toLong()))

        val r = reader(root)
        val first = r.consume()
        assertEquals(1, first.size)
        first.forEach { it.close() }

        val second = r.consume()
        assertTrue(second.isEmpty(), "second consume must return empty when inbox was cleared")
    }

    @Test
    fun `consume with multiple files in one batch cleans staging after all closed`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-3"
        makeDir(inboxBatch)
        val a = "alpha".encodeToByteArray()
        val b = "beta".encodeToByteArray()
        writeFile(inboxBatch, "a.txt", a)
        writeFile(inboxBatch, "b.txt", b)
        writeManifest(inboxBatch, listOf("a.txt" to a.size.toLong(), "b.txt" to b.size.toLong()))

        val sources = reader(root).consume()
        assertEquals(2, sources.size)

        sources[0].close()
        assertTrue(fm.fileExistsAtPath("$root/staging/batch-3"), "staging must still exist after first close")
        sources[1].close()
        assertFalse(fm.fileExistsAtPath("$root/staging/batch-3"), "staging must be deleted after last close")
    }

    @Test
    fun `consume skips batch with missing manifest`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-no-manifest"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "orphan.txt", ByteArray(4))
        // no manifest.json written

        val sources = reader(root).consume()
        assertTrue(sources.isEmpty(), "batch without manifest must be skipped")
    }
}
