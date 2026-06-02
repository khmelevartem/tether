@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import com.tubetoast.tether.TempDirs
import platform.Foundation.NSFileManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleUploadStorageBackendTest {
    private val tempDirs = TempDirs(slug = "tether-upload")

    @AfterTest
    fun cleanup() = tempDirs.cleanup()

    private fun newTempDir(): String = tempDirs.newDir()

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
