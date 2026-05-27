package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel

/**
 * Platform primitives for path-based file storage. Assumes a POSIX-style filesystem
 * (paths are `/`-separated strings; existence and atomic creation are syscall primitives).
 * The future Android MediaStore backend will not implement this interface — it will provide
 * its own [UploadStorage] directly, see file-transfer-wire.md §Storage seam.
 */
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

internal class FileUploadStorage(
    private val root: String,
    private val backend: UploadStorageBackend,
) : UploadStorage {
    override fun ensureRoot() = backend.ensureRoot()

    override fun resolveDestination(relativePath: String): UploadHandle {
        val leafName = relativePath.substringAfterLast('/')
        val parentPath = if (relativePath.contains('/')) {
            "$root/${relativePath.substringBeforeLast('/')}"
        } else {
            root
        }
        val created = mkdirsTracked(parentPath, backend::pathExists, backend::mkdirIfAbsent)
        try {
            val parentReal = backend.realpath(parentPath)
                ?: throw Exception("destination escapes downloads root: realpath failed for $parentPath")
            val rootReal = backend.rootRealPath
            if (parentReal != rootReal && !parentReal.startsWith("$rootReal/")) {
                throw Exception("destination escapes downloads root: $parentReal")
            }
            val leaf = reserveDeduplicatedFile(
                parentPath = parentReal,
                leafName = leafName,
                pathExists = backend::pathExists,
                joinPath = { parent, name -> "$parent/$name" },
                atomicCreate = backend::atomicCreateFile,
            )
            return UploadHandle(destination = "$parentReal/$leaf", createdDirs = created)
        } catch (e: Throwable) {
            created.forEach { backend.deleteDirectoryIfEmpty(it) }
            throw e
        }
    }

    override suspend fun writeBody(body: ByteReadChannel, handle: UploadHandle): Long =
        backend.writeBody(body, handle.destination)

    override fun abort(handle: UploadHandle) {
        backend.deleteFileIfExists(handle.destination)
        handle.createdDirs.forEach { backend.deleteDirectoryIfEmpty(it) }
    }
}

internal fun mkdirsTracked(
    dir: String,
    exists: (String) -> Boolean,
    mkdir: (String) -> Boolean,
): List<String> {
    if (exists(dir)) return emptyList()
    val segments = mutableListOf<String>()
    var cur: String? = dir
    while (cur != null && cur.isNotEmpty() && cur != "/" && !exists(cur)) {
        segments += cur
        val parent = cur.substringBeforeLast('/', missingDelimiterValue = "")
        cur = if (parent.isEmpty() || parent == cur) null else parent
    }
    for (path in segments.asReversed()) {
        mkdir(path)
    }
    return segments
}
