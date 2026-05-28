package com.tubetoast.tether.presentation

data class PendingFilesSummary(
    val fileCount: Int,
    val totalBytes: Long,
) {
    companion object {
        val NONE = PendingFilesSummary(0, 0L)
    }
}
