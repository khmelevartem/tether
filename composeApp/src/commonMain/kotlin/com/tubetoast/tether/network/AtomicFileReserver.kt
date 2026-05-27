package com.tubetoast.tether.network

internal const val MAX_DEDUP_RETRIES = 1000

/**
 * Atomically creates an empty placeholder file at [path].
 * Returns `true` on success, `false` if a file at [path] already exists.
 * Throws an exception for any other I/O error.
 */
internal expect fun atomicCreateFile(path: String): Boolean

/**
 * Deduplicates [leafName] against existing entries in [parentPath] and atomically
 * reserves the chosen name by creating an empty placeholder file.
 *
 * Returns the reserved leaf name, or throws [Exception] if no free name can be
 * found within [MAX_DEDUP_RETRIES] attempts.
 *
 * @param atomicCreate creates an empty file at the given path; returns `true` on success,
 * `false` if already exists. Defaults to [atomicCreateFile].
 */
internal fun reserveDeduplicatedFile(
    parentPath: String,
    leafName: String,
    pathExists: (candidate: String) -> Boolean,
    joinPath: (parent: String, leaf: String) -> String,
    atomicCreate: (path: String) -> Boolean = ::atomicCreateFile,
): String {
    repeat(MAX_DEDUP_RETRIES) {
        val leaf = dedupFilename(leafName) { candidate ->
            pathExists(joinPath(parentPath, candidate))
        }
        val candidatePath = joinPath(parentPath, leaf)
        if (atomicCreate(candidatePath)) return leaf
        // Another concurrent upload claimed this name; retry dedup.
    }
    error("FileServer: could not reserve destination after $MAX_DEDUP_RETRIES attempts")
}
