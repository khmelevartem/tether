@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, ExperimentalAtomicApi::class)

package com.tubetoast.tether.share

import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.IosFileSource
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
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val log = KydraLog.withTag(default = "SharedPendingFilesReader")

/**
 * At-least-once delivery: batches are moved to a staging directory before being returned, so a
 * crash between move and processing causes the batch to be re-read on the next foreground
 * activation.
 *
 * On first [consume] a startup sweep deletes any staging dirs left over from previous sessions
 * (their in-memory references died with the process).
 */
internal class SharedPendingFilesReader(
    private val inboxDir: String,
    private val stagingDir: String,
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

    // Runs under mutex, so no concurrent consume can race the deletion.
    private fun sweepStaleStagingOnce() {
        if (!startupSweepDone.compareAndSet(expectedValue = false, newValue = true)) return
        for (batchPath in listBatchDirs(stagingDir)) {
            deleteStagedBatch(batchPath)
        }
        log.info { "startup staging sweep complete" }
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
            sources += StagedFileSource(inner) {
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

private class StagedFileSource(
    private val inner: IosFileSource,
    private val onLastClose: () -> Unit,
) : FileSource by inner {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        try {
            inner.close()
        } finally {
            onLastClose()
        }
    }
}
