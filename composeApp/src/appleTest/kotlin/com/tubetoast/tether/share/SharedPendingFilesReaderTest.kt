@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.share

import com.tubetoast.tether.TempDirs
import com.tubetoast.tether.transfer.ConfirmableFileSource
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
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
        if (bytes.isNotEmpty()) {
            bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.toULong(), file) }
        }
        fclose(file)
        return path
    }

    private fun makeDir(path: String) {
        fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    }

    private fun backdateDir(path: String, secondsAgo: Double) {
        val oldDate = NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate - secondsAgo)
        fm.setAttributes(mapOf<Any?, Any>(NSFileModificationDate to oldDate), ofItemAtPath = path, error = null)
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
        tmpDir = "$root/tmp",
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

        assertFalse(fm.fileExistsAtPath(inboxBatch), "inbox batch must be gone after consume")
        assertTrue(fm.fileExistsAtPath("$root/staging/batch-1"), "staging batch must exist")

        // IosFileSource.openReadChannel() reads on real Dispatchers.IO, so virtual time
        // cannot gate the read — we drain synchronously via readAvailable in a real coroutine.
        val channel = sources[0].openReadChannel()
        val buf = ByteArray(content.size)
        channel.readAvailable(buf)
        sources[0].close()

        assertTrue(
            fm.fileExistsAtPath("$root/staging/batch-1"),
            "staging must still exist after close() alone",
        )

        (sources[0] as ConfirmableFileSource).confirmDelivered()
        assertFalse(
            fm.fileExistsAtPath("$root/staging/batch-1"),
            "staging must be deleted after confirmDelivered()",
        )

        assertEquals(content.decodeToString(), buf.decodeToString())
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
        first.forEach { (it as ConfirmableFileSource).confirmDelivered() }

        val second = r.consume()
        assertTrue(second.isEmpty(), "second consume must return empty when inbox was cleared")
    }

    @Test
    fun `consume with multiple files in one batch cleans staging after all confirmed`() = runTest {
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
        sources[1].close()
        assertTrue(fm.fileExistsAtPath("$root/staging/batch-3"), "staging must still exist after both closes")

        (sources[0] as ConfirmableFileSource).confirmDelivered()
        assertTrue(fm.fileExistsAtPath("$root/staging/batch-3"), "staging must still exist after first confirm")

        (sources[1] as ConfirmableFileSource).confirmDelivered()
        assertFalse(fm.fileExistsAtPath("$root/staging/batch-3"), "staging must be deleted after last confirm")
    }

    @Test
    fun `consume skips batch with missing manifest and leaves it in place`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-no-manifest"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "orphan.txt", ByteArray(4))

        val sources = reader(root).consume()
        assertTrue(sources.isEmpty(), "batch without manifest must be skipped")
        // The dir was moved to staging before readBatch found the manifest absent.
        // Staging must exist (not deleted) so the data isn't destroyed.
        assertFalse(
            fm.fileExistsAtPath(inboxBatch),
            "inbox batch must be moved to staging (not left in inbox)",
        )
        assertTrue(
            fm.fileExistsAtPath("$root/staging/batch-no-manifest"),
            "manifest-less batch must survive in staging, not be deleted",
        )
    }

    @Test
    fun `consume returns empty and leaves no staging for corrupt manifest — not-json`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-corrupt"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "manifest.json", "not json!!!".encodeToByteArray())

        val sources = reader(root).consume()
        assertTrue(sources.isEmpty(), "corrupt manifest must yield no sources")
        assertFalse(fm.fileExistsAtPath("$root/staging/batch-corrupt"), "staging must be cleaned up")
    }

    @Test
    fun `consume returns empty and leaves no staging for non-array json manifest`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-object-manifest"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "manifest.json", """{"key":"value"}""".encodeToByteArray())

        val sources = reader(root).consume()
        assertTrue(sources.isEmpty(), "non-array JSON manifest must yield no sources")
        assertFalse(fm.fileExistsAtPath("$root/staging/batch-object-manifest"), "staging must be cleaned up")
    }

    @Test
    fun `partial batch — manifest lists 2 files but only 1 exists on disk`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-partial"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "present.txt", "data".encodeToByteArray())
        writeManifest(
            inboxBatch,
            listOf("present.txt" to 4L, "missing.txt" to 10L),
        )

        val sources = reader(root).consume()
        assertEquals(1, sources.size, "only the present file must be returned")
        assertEquals("present.txt", sources[0].name)

        (sources[0] as ConfirmableFileSource).confirmDelivered()
        assertFalse(
            fm.fileExistsAtPath("$root/staging/batch-partial"),
            "staging must be deleted after the only source is confirmed",
        )
    }

    @Test
    fun `double-confirm does not crash and deletion happens exactly once`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-double-close"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "f.txt", "x".encodeToByteArray())
        writeManifest(inboxBatch, listOf("f.txt" to 1L))

        val sources = reader(root).consume()
        assertEquals(1, sources.size)

        // close() alone must not delete
        sources[0].close()
        assertTrue(
            fm.fileExistsAtPath("$root/staging/batch-double-close"),
            "staging must still exist after close()",
        )

        val confirmable = sources[0] as ConfirmableFileSource
        confirmable.confirmDelivered()
        assertFalse(
            fm.fileExistsAtPath("$root/staging/batch-double-close"),
            "staging must be gone after first confirmDelivered()",
        )

        // Second confirm must not crash or re-delete (already gone)
        confirmable.confirmDelivered()
        assertFalse(
            fm.fileExistsAtPath("$root/staging/batch-double-close"),
            "staging still gone after second confirmDelivered()",
        )
    }

    @Test
    fun `failure-never-deletes — close without confirm leaves staging intact`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-fail"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "f.txt", "data".encodeToByteArray())
        writeManifest(inboxBatch, listOf("f.txt" to 4L))

        val sources = reader(root).consume()
        assertEquals(1, sources.size)

        // Simulate a failed send attempt: PeerFileSender finally-closes the source but never
        // calls confirmDelivered — staging must survive so retries can re-read the file.
        sources[0].close()

        assertTrue(
            fm.fileExistsAtPath("$root/staging/batch-fail"),
            "staging must survive close() without confirmDelivered() so retries work",
        )
    }

    @Test
    fun `retry-then-confirm — two-file batch survives first attempt and deletes on second confirm`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-retry"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "a.txt", "aaa".encodeToByteArray())
        writeFile(inboxBatch, "b.txt", "bbb".encodeToByteArray())
        writeManifest(inboxBatch, listOf("a.txt" to 3L, "b.txt" to 3L))

        val sources = reader(root).consume()
        assertEquals(2, sources.size)
        val confirmA = sources[0] as ConfirmableFileSource
        val confirmB = sources[1] as ConfirmableFileSource

        // Attempt 1: confirm A, close both (B failed)
        confirmA.confirmDelivered()
        sources[0].close()
        sources[1].close()
        assertTrue(
            fm.fileExistsAtPath("$root/staging/batch-retry"),
            "staging must survive after A confirmed and B not yet confirmed",
        )

        // Attempt 2: B succeeds — confirm B
        confirmB.confirmDelivered()
        assertFalse(
            fm.fileExistsAtPath("$root/staging/batch-retry"),
            "staging must be deleted after both sources confirmed",
        )
    }

    @Test
    fun `contamination guard — confirming one batch does not delete another batch's staging`() = runTest {
        val root = makeRoot()

        // Batch A
        val inboxBatchA = "$root/inbox/batch-A"
        makeDir(inboxBatchA)
        writeFile(inboxBatchA, "file.txt", "aaaa".encodeToByteArray())
        writeManifest(inboxBatchA, listOf("file.txt" to 4L))

        // Batch B (same filename, different batchID)
        val inboxBatchB = "$root/inbox/batch-B"
        makeDir(inboxBatchB)
        writeFile(inboxBatchB, "file.txt", "bbbb".encodeToByteArray())
        writeManifest(inboxBatchB, listOf("file.txt" to 4L))

        val r = reader(root)
        val first = r.consume()
        // Both batches were in inbox at the same time; consume moves both.
        assertEquals(2, first.size)

        // Find which source belongs to which staging dir by checking file content.
        val stagingA = "$root/staging/batch-A"
        val stagingB = "$root/staging/batch-B"
        assertTrue(fm.fileExistsAtPath(stagingA), "staging A must exist")
        assertTrue(fm.fileExistsAtPath(stagingB), "staging B must exist")

        // Confirm only A's source (whichever reads from staging/batch-A).
        val sourceA = first.first { fm.fileExistsAtPath("$stagingA/${it.name}") }
        (sourceA as ConfirmableFileSource).confirmDelivered()

        assertFalse(fm.fileExistsAtPath(stagingA), "staging A must be deleted after A's source confirmed")
        assertTrue(fm.fileExistsAtPath(stagingB), "staging B must be untouched when only A was confirmed")
    }

    @Test
    fun `zero-byte file — source returned with sizeBytes 0 and channel closes cleanly`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-zero"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "empty.bin", ByteArray(0))
        writeManifest(inboxBatch, listOf("empty.bin" to 0L))

        val sources = reader(root).consume()
        assertEquals(1, sources.size)
        assertEquals("empty.bin", sources[0].name)
        assertEquals(0L, sources[0].sizeBytes)

        // IosFileSource.openReadChannel() reads on real Dispatchers.IO, so virtual time
        // cannot gate the read — confirm channel is immediately exhausted.
        val channel = sources[0].openReadChannel()
        val buf = ByteArray(16)
        val bytesRead = channel.readAvailable(buf)
        assertTrue(bytesRead <= 0, "empty file must yield no bytes")
        assertTrue(channel.isClosedForRead, "channel must be closed after EOF")

        sources[0].close()
    }

    @Test
    fun `startup sweep removes stale staging dirs left from a previous session`() = runTest {
        val root = makeRoot()
        val staleDir = "$root/staging/stale-batch"
        makeDir(staleDir)
        writeFile(staleDir, "leftover.txt", ByteArray(4))
        assertTrue(fm.fileExistsAtPath(staleDir), "stale dir must exist before reader is used")

        val sources = reader(root).consume()
        assertTrue(sources.isEmpty(), "inbox is empty so consume returns nothing")
        assertFalse(fm.fileExistsAtPath(staleDir), "startup sweep must have removed the stale staging dir")
    }

    @Test
    fun `startup sweep removes old tmp dirs left from a previous extension session`() = runTest {
        val root = makeRoot()
        val staleTmp = "$root/tmp/abandoned-batch"
        makeDir(staleTmp)
        writeFile(staleTmp, "partial.bin", ByteArray(8))
        backdateDir(staleTmp, secondsAgo = 4000.0)
        assertTrue(fm.fileExistsAtPath(staleTmp), "old tmp dir must exist before reader is used")

        reader(root).consume()
        assertFalse(fm.fileExistsAtPath(staleTmp), "startup sweep must have removed the old tmp dir")
    }

    @Test
    fun `startup sweep preserves recent tmp dir that may belong to an in-flight extension copy`() = runTest {
        val root = makeRoot()
        val activeTmp = "$root/tmp/in-flight-batch"
        makeDir(activeTmp)
        writeFile(activeTmp, "partial.bin", ByteArray(8))
        assertTrue(fm.fileExistsAtPath(activeTmp), "recent tmp dir must exist before reader is used")

        reader(root).consume()
        assertTrue(fm.fileExistsAtPath(activeTmp), "startup sweep must not delete a recently-modified tmp dir")
    }

    @Test
    fun `startup sweep runs at most once — live staging dirs from first consume survive second consume`() = runTest {
        val root = makeRoot()
        val inboxBatch = "$root/inbox/batch-once"
        makeDir(inboxBatch)
        writeFile(inboxBatch, "f.txt", "data".encodeToByteArray())
        writeManifest(inboxBatch, listOf("f.txt" to 4L))

        val r = reader(root)
        val first = r.consume()
        assertEquals(1, first.size)

        val stagingBatch = "$root/staging/batch-once"
        assertTrue(fm.fileExistsAtPath(stagingBatch), "staging must exist while sources are open")

        val second = r.consume()
        assertTrue(second.isEmpty(), "inbox is empty so second consume returns nothing")
        assertTrue(
            fm.fileExistsAtPath(stagingBatch),
            "second consume must not sweep live staging dirs from the first consume",
        )

        (first[0] as ConfirmableFileSource).confirmDelivered()
    }
}
