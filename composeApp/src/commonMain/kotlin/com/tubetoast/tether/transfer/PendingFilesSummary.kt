package com.tubetoast.tether.transfer

data class PendingFilesSummary(
    val fileCount: Int,
    val totalBytes: Long,
) {
    val isLargeSelection: Boolean
        get() = fileCount > LARGE_FILE_COUNT || totalBytes > LARGE_TOTAL_BYTES

    companion object {
        const val LARGE_FILE_COUNT = 500
        const val LARGE_TOTAL_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

        fun from(sources: List<FileSource>) = PendingFilesSummary(
            fileCount = sources.size,
            totalBytes = sources.sumOf { it.sizeBytes ?: 0L },
        )
    }
}
