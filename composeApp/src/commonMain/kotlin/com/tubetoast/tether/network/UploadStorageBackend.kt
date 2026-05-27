package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel

internal interface UploadStorageBackend {
    /** Resolved real path of the downloads root. May throw if the root is unresolvable. */
    val rootRealPath: String

    /** Ensures the downloads root exists, creating it if missing. Idempotent. */
    fun ensureRoot()

    /** Returns `true` if any filesystem entry (file or directory) exists at [path]. */
    fun pathExists(path: String): Boolean

    /** Returns `true` if the directory was created, `false` if it already existed. Throws on I/O error. */
    fun mkdirIfAbsent(path: String): Boolean

    /** Deletes the file at [path]. No-op if absent. Does not follow symlinks beyond what the OS does. */
    fun deleteFileIfExists(path: String)

    /** Returns `true` if deleted, `false` if not empty or not present. */
    fun deleteDirectoryIfEmpty(path: String): Boolean

    /** Returns the real (canonicalized) path, or `null` on any I/O error. */
    fun realpath(path: String): String?

    /** Atomically creates the file. Returns `true` on success, `false` if it already exists. Throws on I/O error. */
    fun atomicCreateFile(path: String): Boolean

    /** Streams [body] into the file at [destination]; returns bytes written. Throws on I/O error or short write. */
    suspend fun writeBody(body: ByteReadChannel, destination: String): Long
}
