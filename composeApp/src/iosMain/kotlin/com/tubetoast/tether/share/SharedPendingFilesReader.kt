@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, ExperimentalAtomicApi::class)

package com.tubetoast.tether.share

import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.IosFileSource
import com.tubetoast.tether.transfer.OnCloseFileSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSURL
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val log = KydraLog.withTag(default = "SharedPendingFilesReader")

// Cross-process: a tmp dir younger than this may belong to an in-flight extension copy.
private const val TMP_STALE_THRESHOLD_SECONDS = 300.0

/**
 * Published batches are read at least once: a batch is moved to a staging directory before being
 * returned, so a crash between move and ingest causes it to be re-read on the next foreground
 * activation.
 *
 * On first [consume] a startup sweep clears stale staging dirs and abandoned tmp dirs left over
 * from previous sessions.
 */
internal class SharedPendingFilesReader(
    private val inboxDir: String,
    private val stagingDir: String,
    private val tmpDir: String,
) {
    private val mutex = Mutex()
    private val fm = NSFileManager.defaultManager
    private val startupSweepDone = AtomicBoolean(false)

    suspend fun consume(): List<FileSource> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureDir(stagingDir)
            sweepStaleStagingOnce()
            val batches = listBatchDirs(inboxDir)
            if (batches.isEmpty()) return@withContext emptyList()

            val sources = mutableListOf<FileSource>()
            for (batchPath in batches) {
                val stagedPath = moveBatchToStaging(batchPath) ?: continue
                sources += readBatch(stagedPath)
            }
            log.info { "consume: ${sources.size} file(s) from ${batches.size} batch(es)" }
            sources
        }
    }

    // Runs under mutex, so no concurrent consume can race the deletions.
    private fun sweepStaleStagingOnce() {
        if (!startupSweepDone.compareAndSet(expectedValue = false, newValue = true)) return
        for (batchPath in listBatchDirs(stagingDir)) {
            deleteStagedBatch(batchPath)
        }
        for (batchPath in listBatchDirs(tmpDir)) {
            if (isTmpDirStale(batchPath)) deleteStagedBatch(batchPath)
        }
        log.info { "startup sweep complete" }
    }

    private fun listBatchDirs(parentPath: String): List<String> {
        ensureDir(parentPath)
        val contents = memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            fm.contentsOfDirectoryAtPath(parentPath, error = errorPtr.ptr).also {
                if (it == null) {
                    log.warn { "cannot list inbox: ${errorPtr.value?.localizedDescription}" }
                }
            }
        } ?: return emptyList()
        return contents.filterIsInstance<String>().map { "$parentPath/$it" }.filter { isDir(it) }
    }

    private fun moveBatchToStaging(batchPath: String): String? {
        val batchName = batchPath.substringAfterLast('/')
        val dest = "$stagingDir/$batchName"
        // batchIDs are unique UUIDs; a pre-existing staging dir indicates a bug or race —
        // skip rather than silently reusing and corrupting a live ref-count tree.
        if (isDir(dest)) {
            log.warn { "staging collision for '$batchName' — skipping" }
            return null
        }
        return memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val ok = fm.moveItemAtPath(batchPath, toPath = dest, error = errorPtr.ptr)
            if (!ok) {
                log.warn { "move batch failed '$batchName': ${errorPtr.value?.localizedDescription}" }
                null
            } else {
                dest
            }
        }
    }

    private fun readBatch(batchPath: String): List<FileSource> {
        val manifestPath = "$batchPath/manifest.json"
        if (!fm.fileExistsAtPath(manifestPath)) {
            // Inbox is supposed to be written atomically (manifest last, then renamed in).
            // A batch without a manifest here is unexpected — leave it rather than destroying it.
            log.warn { "manifest absent in staged batch '${batchPath.substringAfterLast('/')}' — leaving in place" }
            return emptyList()
        }
        val entries = parseManifest(manifestPath)
        if (entries.isEmpty()) {
            deleteStagedBatch(batchPath)
            return emptyList()
        }

        val remaining = AtomicInt(entries.size)
        val sources = mutableListOf<FileSource>()
        for (entry in entries) {
            val filePath = "$batchPath/${entry.name}"
            if (!fm.fileExistsAtPath(filePath)) {
                if (remaining.addAndFetch(-1) == 0) deleteStagedBatch(batchPath)
                continue
            }
            val url = NSURL.fileURLWithPath(filePath)
            val inner = IosFileSource(
                url = url,
                relativePath = entry.name,
                sizeBytes = entry.size,
                securityScoped = false,
            )
            sources += OnCloseFileSource(inner) {
                if (remaining.addAndFetch(-1) == 0) deleteStagedBatch(batchPath)
            }
        }
        if (sources.isEmpty() && remaining.load() > 0) deleteStagedBatch(batchPath)
        return sources
    }

    private fun parseManifest(path: String): List<ManifestEntry> {
        if (!fm.fileExistsAtPath(path)) return emptyList()
        val data = fm.contentsAtPath(path) ?: return emptyList()
        return try {
            val json = platform.Foundation.NSJSONSerialization.JSONObjectWithData(
                data,
                options = 0u,
                error = null,
            ) as? List<*> ?: return emptyList()
            json.filterIsInstance<Map<*, *>>().mapNotNull { entry ->
                val name = entry["name"] as? String ?: return@mapNotNull null
                val size = (entry["size"] as? Number)?.toLong()
                ManifestEntry(name, size)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isTmpDirStale(batchPath: String): Boolean {
        val attrs = fm.attributesOfItemAtPath(batchPath, error = null) ?: return true
        val mtime = attrs[NSFileModificationDate] as? NSDate ?: return true
        return NSDate().timeIntervalSinceReferenceDate - mtime.timeIntervalSinceReferenceDate >
            TMP_STALE_THRESHOLD_SECONDS
    }

    private fun deleteStagedBatch(batchPath: String) {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val ok = fm.removeItemAtPath(batchPath, error = errorPtr.ptr)
            if (!ok) {
                log.warn { "delete staged batch failed: ${errorPtr.value?.localizedDescription}" }
            }
        }
    }

    private fun ensureDir(path: String) {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = errorPtr.ptr)
        }
    }

    private fun isDir(path: String): Boolean = memScoped {
        val boolRef = alloc<kotlinx.cinterop.BooleanVar>()
        fm.fileExistsAtPath(path, isDirectory = boolRef.ptr)
        boolRef.value
    }

    private data class ManifestEntry(
        val name: String,
        val size: Long?,
    )
}
