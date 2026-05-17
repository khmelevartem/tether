package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.FakeFileSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoftThresholdTest {
    @Test
    fun below499FilesDoesNotTrigger() {
        val sources = List(499) { FakeFileSource("file$it.txt", ByteArray(1), size = 1L) }
        assertFalse(exceedsThreshold(sources))
    }

    @Test
    fun exactly500FilesTriggersThreshold() {
        val sources = List(500) { FakeFileSource("file$it.txt", ByteArray(1), size = 1L) }
        assertTrue(exceedsThreshold(sources))
    }

    @Test
    fun below1GbDoesNotTrigger() {
        val sources = listOf(FakeFileSource("big.bin", size = TOTAL_BYTES_THRESHOLD - 1))
        assertFalse(exceedsThreshold(sources))
    }

    @Test
    fun exactly1GbTriggersThreshold() {
        val sources = listOf(FakeFileSource("big.bin", size = TOTAL_BYTES_THRESHOLD))
        assertTrue(exceedsThreshold(sources))
    }

    @Test
    fun eitherConditionAloneTriggersThreshold() {
        val byCountOnly = List(FILE_COUNT_THRESHOLD) { FakeFileSource("f$it.txt", size = 1L) }
        assertTrue(exceedsThreshold(byCountOnly))

        val bySizeOnly = listOf(FakeFileSource("big.bin", size = TOTAL_BYTES_THRESHOLD))
        assertTrue(exceedsThreshold(bySizeOnly))
    }

    @Test
    fun manySmallFilesExceedingBothThresholds() {
        val sources = List(FILE_COUNT_THRESHOLD + 1) {
            FakeFileSource("f$it.bin", size = TOTAL_BYTES_THRESHOLD / FILE_COUNT_THRESHOLD + 1)
        }
        assertTrue(exceedsThreshold(sources))
    }
}
