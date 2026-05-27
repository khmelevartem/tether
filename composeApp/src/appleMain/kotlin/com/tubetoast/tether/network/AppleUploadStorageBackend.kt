@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.posix.EEXIST
import platform.posix.PATH_MAX
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.realpath

internal class AppleUploadStorageBackend(
    private val rootPath: String,
) : UploadStorageBackend {
    override val rootRealPath: String by lazy {
        realpathOf(rootPath) ?: throw IOException("FileServer: realpath failed for downloads root: $rootPath")
    }

    override fun ensureRoot() = mkdirsChecked(rootPath)

    override fun pathExists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    override fun mkdirIfAbsent(path: String): Boolean {
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(path)) return false
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val ok = fm.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = false,
                attributes = null,
                error = errorPtr.ptr,
            )
            // Concurrent sibling may have created the directory between exists() and createDirectory().
            if (!ok && !fm.fileExistsAtPath(path)) {
                val msg = errorPtr.value?.localizedDescription ?: "unknown error"
                throw IOException("FileServer: mkdir failed for $path: $msg")
            }
        }
        return true
    }

    override fun deleteFileIfExists(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    override fun deleteDirectoryIfEmpty(path: String): Boolean {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(path, null) ?: return false
        if (contents.isNotEmpty()) return false
        return fm.removeItemAtPath(path, error = null)
    }

    override fun realpath(path: String): String? = realpathOf(path)

    override fun atomicCreateFile(path: String): Boolean {
        val file = fopen(path, "wbx")
        if (file != null) {
            fclose(file)
            return true
        }
        if (errno == EEXIST) return false
        throw IOException("FileServer: could not create placeholder '$path'")
    }

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long {
        val file = fopen(destination, "wb")
            ?: throw IOException("FileServer: could not open '$destination' for writing")
        var total = 0L
        try {
            streamUploadBody(body) { buffer, n ->
                buffer.usePinned { pinned ->
                    // POSIX: short fwrite return signals an I/O error (disk full, quota, etc.).
                    // Without the check the upload would silently truncate while responding 200 OK.
                    val written = fwrite(pinned.addressOf(0), 1u, n.toULong(), file).toLong()
                    if (written < n.toLong()) {
                        throw IOException("FileServer: short write to '$destination' — wrote $written of $n bytes")
                    }
                }
                total += n.toLong()
            }
            // fflush surfaces deferred stdio errors before the route responds.
            // fclose still runs in finally; its error is ignored because fflush
            // already validated the data reached the OS.
            if (fflush(file) != 0) {
                throw IOException("FileServer: fflush failed for '$destination'")
            }
        } finally {
            fclose(file)
        }
        return total
    }
}

private fun mkdirsChecked(path: String) {
    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(path)) return
    memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val ok = fm.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = errorPtr.ptr,
        )
        if (!ok) {
            val msg = errorPtr.value?.localizedDescription ?: "unknown error"
            throw IOException("FileServer: createDirectory failed for $path: $msg")
        }
    }
}

private fun realpathOf(path: String): String? = memScoped {
    val buf = allocArray<ByteVar>(PATH_MAX)
    val result = realpath(path, buf) ?: return null
    result.toKString()
}
