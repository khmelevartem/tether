@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleUploadStorageBackendTest {
    private val tempDirs = mutableListOf<String>()

    @AfterTest
    fun cleanup() {
        val fm = NSFileManager.defaultManager
        tempDirs.forEach { fm.removeItemAtPath(it, error = null) }
        tempDirs.clear()
    }

    private fun newTempDir(): String {
        val path = "${NSTemporaryDirectory()}tether-apple-backend-${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        tempDirs += path
        return path
    }

    @Test
    fun mkdirIfAbsent_returns_true_when_directory_is_newly_created() {
        val root = newTempDir()
        val backend = AppleUploadStorageBackend(root)
        val target = "$root/newdir"
        assertTrue(backend.mkdirIfAbsent(target))
    }

    @Test
    fun mkdirIfAbsent_returns_false_when_directory_already_exists() {
        val root = newTempDir()
        val backend = AppleUploadStorageBackend(root)
        val target = "$root/existingdir"
        assertTrue(backend.mkdirIfAbsent(target), "first call must create and return true")
        assertFalse(backend.mkdirIfAbsent(target), "second call must return false, not throw")
    }

    @Test
    fun mkdirIfAbsent_returns_false_when_directory_was_created_externally_before_the_call() {
        val root = newTempDir()
        val backend = AppleUploadStorageBackend(root)
        val target = "$root/pre-created"
        NSFileManager.defaultManager.createDirectoryAtPath(
            target,
            withIntermediateDirectories = false,
            attributes = null,
            error = null,
        )
        assertFalse(backend.mkdirIfAbsent(target))
    }

    @Test
    fun mkdirIfAbsent_throws_when_parent_does_not_exist() {
        val root = newTempDir()
        val backend = AppleUploadStorageBackend(root)
        val noParent = "$root/nonexistent-parent/child"
        assertFailsWith<Exception> { backend.mkdirIfAbsent(noParent) }
    }
}
