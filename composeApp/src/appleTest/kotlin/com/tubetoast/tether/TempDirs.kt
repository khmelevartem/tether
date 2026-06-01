@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tubetoast.tether

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * Per-test scratch-directory tracker. Each call to [newDir] returns a fresh path under
 * `NSTemporaryDirectory()` and remembers it; [cleanup] removes every tracked directory.
 * Owners call [cleanup] from an `@AfterTest`.
 */
internal class TempDirs(
    private val slug: String = "tether",
) {
    private val paths = mutableListOf<String>()

    fun newDir(): String {
        val path = "${NSTemporaryDirectory()}$slug-${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        paths.add(path)
        return path
    }

    fun cleanup() {
        val fm = NSFileManager.defaultManager
        paths.forEach { fm.removeItemAtPath(it, error = null) }
        paths.clear()
    }
}
