package com.tubetoast.tether.network

internal const val MAX_DEDUP_RETRIES = 1000

/**
 * Deduplicates [leafName] against existing entries in [parentPath] and atomically
 * reserves the chosen name by creating an empty placeholder file.
 *
 * Returns the reserved leaf name, or throws if no free name can be found within [MAX_DEDUP_RETRIES] attempts.
 */
internal fun reserveDeduplicatedFile(
    parentPath: String,
    leafName: String,
    pathExists: (candidate: String) -> Boolean,
    joinPath: (parent: String, leaf: String) -> String,
    atomicCreate: (path: String) -> Boolean,
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
