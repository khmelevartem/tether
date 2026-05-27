package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileUploadStorageTest {
    private class FakeBackend(
        override val rootRealPath: String = "/real/root",
        private val realpathMap: Map<String, String> = emptyMap(),
        private val atomicCreateResults: ArrayDeque<Boolean> = ArrayDeque<Boolean>().also { it.add(true) },
    ) : UploadStorageBackend {
        val createdDirs = mutableListOf<String>()
        val deletedFiles = mutableListOf<String>()
        val deletedDirs = mutableListOf<String>()
        private val existingPaths = mutableSetOf<String>()

        override fun ensureRoot() {}

        override fun pathExists(path: String): Boolean = path in existingPaths

        override fun mkdirIfAbsent(path: String): Boolean {
            if (path in existingPaths) return false
            createdDirs += path
            existingPaths += path
            return true
        }

        override fun deleteFileIfExists(path: String) {
            deletedFiles += path
        }

        override fun deleteDirectoryIfEmpty(path: String): Boolean {
            deletedDirs += path
            return true
        }

        override fun realpath(path: String): String? = realpathMap[path]

        override fun atomicCreateFile(path: String): Boolean {
            val result = atomicCreateResults.removeFirstOrNull() ?: true
            if (result) existingPaths += path
            return result
        }

        override suspend fun writeBody(body: ByteReadChannel, destination: String): Long = 0L
    }

    @Test
    fun resolveDestination_returns_handle_with_destination_and_created_dirs() {
        val backend = FakeBackend(
            rootRealPath = "/real/root",
            realpathMap = mapOf("/real/root/sub" to "/real/root/sub"),
        )
        val storage = FileUploadStorage("/real/root", backend)

        val handle = storage.resolveDestination("sub/file.txt")

        assertEquals("/real/root/sub/file.txt", handle.destination)
        assertTrue(handle.createdDirs.isNotEmpty())
    }

    @Test
    fun resolveDestination_retries_dedup_when_atomicCreate_returns_false() {
        val atomicResults = ArrayDeque(listOf(false, false, true))
        val backend = FakeBackend(
            rootRealPath = "/real/root",
            realpathMap = mapOf("/real/root" to "/real/root"),
            atomicCreateResults = atomicResults,
        )
        val storage = FileUploadStorage("/real/root", backend)

        val handle = storage.resolveDestination("file.txt")

        assertTrue(atomicResults.isEmpty(), "all three atomicCreate calls must be consumed")
        assertTrue(handle.destination.startsWith("/real/root/file"))
    }

    @Test
    fun resolveDestination_rolls_back_created_dirs_on_escape_check_failure() {
        val backend = FakeBackend(
            rootRealPath = "/real/root",
            realpathMap = mapOf("/real/root/evil" to "/outside/evil"),
        )
        val storage = FileUploadStorage("/real/root", backend)

        assertFailsWith<Exception> { storage.resolveDestination("evil/file.txt") }

        assertTrue(backend.deletedDirs.isNotEmpty())
        backend.createdDirs.forEach { created ->
            assertTrue(created in backend.deletedDirs, "expected $created to be in deleted dirs")
        }
    }

    @Test
    fun abort_deletes_file_then_calls_deleteDirectoryIfEmpty_for_each_created_dir_in_order() {
        val backend = FakeBackend()
        val storage = FileUploadStorage("/real/root", backend)
        val handle = UploadHandle(
            destination = "/real/root/sub/child/file.txt",
            createdDirs = listOf("/real/root/sub/child", "/real/root/sub"),
        )

        storage.abort(handle)

        assertEquals(listOf("/real/root/sub/child/file.txt"), backend.deletedFiles)
        assertEquals(listOf("/real/root/sub/child", "/real/root/sub"), backend.deletedDirs)
    }
}
