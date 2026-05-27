package com.tubetoast.tether.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtomicFileReserverTest {
    @Test
    fun returns_reserved_leaf_after_losing_race_then_winning() {
        var callCount = 0
        val reserved = reserveDeduplicatedFile(
            parentPath = "/fake",
            leafName = "file.txt",
            pathExists = { false },
            joinPath = { parent, name -> "$parent/$name" },
            atomicCreate = { _ -> ++callCount > 3 },
        )
        assertEquals("file.txt", reserved)
        assertEquals(4, callCount)
    }

    @Test
    fun returns_deduplicated_leaf_when_initial_name_already_exists() {
        val existingNames = setOf("file.txt", "file_1.txt")
        val reserved = reserveDeduplicatedFile(
            parentPath = "/fake",
            leafName = "file.txt",
            pathExists = { path -> path.substringAfterLast('/') in existingNames },
            joinPath = { parent, name -> "$parent/$name" },
            atomicCreate = { _ -> true },
        )
        assertEquals("file_2.txt", reserved)
    }

    @Test
    fun exhaustion_throws_after_max_retries() {
        var callCount = 0
        val exception = assertFailsWith<IllegalStateException> {
            reserveDeduplicatedFile(
                parentPath = "/fake",
                leafName = "file.txt",
                pathExists = { false },
                joinPath = { parent, name -> "$parent/$name" },
                atomicCreate = { _ ->
                    callCount++
                    false
                },
            )
        }
        assertTrue(callCount == MAX_DEDUP_RETRIES, "expected $MAX_DEDUP_RETRIES attempts, got $callCount")
        assertTrue(exception.message!!.contains("$MAX_DEDUP_RETRIES"), "exception must mention retry count")
    }
}
