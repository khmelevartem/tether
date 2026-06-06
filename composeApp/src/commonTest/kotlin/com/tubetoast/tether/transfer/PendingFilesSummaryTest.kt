package com.tubetoast.tether.transfer

import kotlin.test.Test
import kotlin.test.assertEquals

class PendingFilesSummaryTest {
    @Test
    fun `from counts null-size source toward fileCount and contributes 0 to totalBytes`() {
        val sources = listOf(
            FakeFileSource("known.txt", sizeBytes = 500L),
            FakeFileSource("unknown.bin", sizeBytes = null),
        )

        val summary = PendingFilesSummary.from(sources)

        assertEquals(2, summary.fileCount)
        assertEquals(500L, summary.totalBytes)
    }
}
