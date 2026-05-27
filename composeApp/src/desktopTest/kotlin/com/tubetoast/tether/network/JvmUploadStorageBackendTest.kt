package com.tubetoast.tether.network

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmUploadStorageBackendTest {
    @Test
    fun `mkdirIfAbsent returns true when directory is newly created`() {
        val root = Files.createTempDirectory("tether-jvm-backend-test").toFile()
        try {
            val backend = JvmUploadStorageBackend(root.absolutePath)
            val target = "${root.absolutePath}/newdir"
            assertTrue(backend.mkdirIfAbsent(target))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `mkdirIfAbsent returns false when directory already exists`() {
        val root = Files.createTempDirectory("tether-jvm-backend-test").toFile()
        try {
            val backend = JvmUploadStorageBackend(root.absolutePath)
            val target = "${root.absolutePath}/existingdir"
            assertTrue(backend.mkdirIfAbsent(target), "first call must create and return true")
            assertFalse(backend.mkdirIfAbsent(target), "second call must return false, not throw")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `mkdirIfAbsent returns false when directory was created externally before the call`() {
        val root = Files.createTempDirectory("tether-jvm-backend-test").toFile()
        try {
            val backend = JvmUploadStorageBackend(root.absolutePath)
            val target = root.resolve("pre-created")
            target.mkdir()
            assertFalse(backend.mkdirIfAbsent(target.absolutePath))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `mkdirIfAbsent throws when parent does not exist`() {
        val root = Files.createTempDirectory("tether-jvm-backend-test").toFile()
        try {
            val backend = JvmUploadStorageBackend(root.absolutePath)
            val noParent = "${root.absolutePath}/nonexistent-parent/child"
            assertFailsWith<Exception> { backend.mkdirIfAbsent(noParent) }
        } finally {
            root.deleteRecursively()
        }
    }
}
