package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel

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
    return segments.toList()
}
