@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

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

private val log = KydraLog.withTag(default = "SharedPendingFilesReader")

/**
 * Reads file batches deposited by TetherShareExtension into the shared app-group inbox.
 *
 * Each call to [consume] is at-least-once: batches are moved to a staging directory before
 * being returned, so a crash between move and processing causes the batch to be re-read on
 * the next foreground activation. A [Mutex] ensures only one [consume] runs at a time.
 *
 * @param inboxDir path to `<appGroupContainer>/inbox`
 * @param stagingDir path to `<appGroupContainer>/staging`
 */
internal class SharedPendingFilesReader(
    private val inboxDir: String,
    private val stagingDir: String,
) {
    private val mutex = Mutex()
    private val fm = NSFileManager.defaultManager

    suspend fun consume(): List<FileSource> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureDir(stagingDir)
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
        if (isDir(dest)) return dest
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

        val delegate = object {
            var remaining = entries.size
        }
        val sources = mutableListOf<FileSource>()
        for (entry in entries) {
            val filePath = "$batchPath/${entry.name}"
            if (!fm.fileExistsAtPath(filePath)) {
                delegate.remaining--
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
                delegate.remaining--
                if (delegate.remaining == 0) deleteStagedBatch(batchPath)
            }
        }
        if (sources.isEmpty()) deleteStagedBatch(batchPath)
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
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        inner.close()
        onLastClose()
    }
}
