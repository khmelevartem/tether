package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.IOException

internal class JvmUploadStorageBackend(
    rootPath: String,
) : UploadStorageBackend {
    // Normalise to forward-slash so commonMain path logic works on Windows too.
    private val rootPath: String = rootPath.replace(File.separatorChar, '/')

    override val rootRealPath: String by lazy {
        File(rootPath)
            .toPath()
            .toRealPath()
            .toString()
            .replace(File.separatorChar, '/')
    }

    override fun ensureRoot() {
        File(rootPath).mkdirs()
    }

    override fun pathExists(path: String): Boolean = File(path).exists()

    override fun mkdirIfAbsent(path: String): Boolean {
        val dir = File(path)
        if (dir.exists()) return false
        if (dir.mkdir()) return true
        // Concurrent sibling may have created the directory between exists() and mkdir().
        if (dir.exists()) return false
        throw IOException("FileServer: mkdir failed for $path")
    }

    override fun deleteFileIfExists(path: String) {
        File(path).delete()
    }

    override fun deleteDirectoryIfEmpty(path: String): Boolean {
        val dir = File(path)
        return dir.exists() && dir.list()?.isEmpty() == true && dir.delete()
    }

    override fun realpath(path: String): String? = try {
        File(path)
            .toPath()
            .toRealPath()
            .toString()
            .replace(File.separatorChar, '/')
    } catch (_: Throwable) {
        null
    }

    override fun atomicCreateFile(path: String): Boolean = File(path).createNewFile()

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long =
        body.toInputStream().use { input ->
            File(destination).outputStream().use { output ->
                input.copyTo(output, bufferSize = UPLOAD_BUFFER_SIZE)
            }
        }
}
