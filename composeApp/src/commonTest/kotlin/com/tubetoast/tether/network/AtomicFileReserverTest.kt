package com.tubetoast.tether.network

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtomicFileReserverTest {
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
