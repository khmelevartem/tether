package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel

internal interface UploadStorageBackend {
    /** Resolved real path of the downloads root. May throw if the root is unresolvable. */
    val rootRealPath: String

    fun ensureRoot()

    fun pathExists(path: String): Boolean

    /** Returns `true` if the directory was created, `false` if it already existed. Throws on I/O error. */
    fun mkdirIfAbsent(path: String): Boolean

    fun deleteFileIfExists(path: String)

    /** Returns `true` if deleted, `false` if not empty or not present. */
    fun deleteDirectoryIfEmpty(path: String): Boolean

    /** Returns the real (canonicalized) path, or `null` on any I/O error. */
    fun realpath(path: String): String?

    /** Atomically creates the file. Returns `true` on success, `false` if it already exists. Throws on I/O error. */
    fun atomicCreateFile(path: String): Boolean

    suspend fun writeBody(body: ByteReadChannel, destination: String): Long
}
