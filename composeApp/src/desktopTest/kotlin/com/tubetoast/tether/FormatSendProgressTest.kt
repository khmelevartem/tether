package com.tubetoast.tether

import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PerFileStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatSendProgressTest {
    private fun sendingState(
        sentBytes: Long = 0L,
        totalBytes: Long? = null,
        bytesPerSec: Long? = null,
        currentFile: String = "file.txt",
        currentIndex: Int = 0,
        totalFiles: Int = 1,
    ) = PeerTransferState.ActiveOutbound.Sending(
        currentFile = currentFile,
        currentIndex = currentIndex,
        totalFiles = totalFiles,
        sentBytes = sentBytes,
        totalBytes = totalBytes,
        bytesPerSec = bytesPerSec,
        skippedCount = 0,
        perFile = listOf(PerFileStatus.InProgress(name = currentFile, size = totalBytes, bytesDone = sentBytes)),
    )

    @Test
    fun `known total renders size and speed`() {
        val state = sendingState(sentBytes = 2_097_152L, totalBytes = 4_194_304L, bytesPerSec = 1_048_576L)
        val line = formatSendProgress(state)
        assertEquals("[send] 2.0 MB / 4.0 MB  1.0 MB/s  (1/1 file.txt)", line)
    }

    @Test
    fun `null total renders question mark`() {
        val state = sendingState(sentBytes = 1_024L, totalBytes = null, bytesPerSec = 512L)
        val line = formatSendProgress(state)
        assertTrue(line.contains("/ ?"), "Expected '/ ?' for unknown total but got: $line")
    }

    @Test
    fun `null bytesPerSec renders Calculating`() {
        val state = sendingState(sentBytes = 0L, totalBytes = 1_048_576L, bytesPerSec = null)
        val line = formatSendProgress(state)
        assertTrue(line.contains("Calculating…"), "Expected 'Calculating…' for null speed but got: $line")
    }

    @Test
    fun `file identity included for multi-file`() {
        val state = sendingState(
            sentBytes = 0L,
            totalBytes = null,
            currentFile = "report.pdf",
            currentIndex = 1,
            totalFiles = 3,
        )
        val line = formatSendProgress(state)
        assertTrue(line.contains("2/3 report.pdf"), "Expected file identity '2/3 report.pdf' but got: $line")
    }

    @Test
    fun `line starts with send prefix`() {
        val state = sendingState()
        assertTrue(formatSendProgress(state).startsWith("[send]"), "Expected [send] prefix")
    }
}
