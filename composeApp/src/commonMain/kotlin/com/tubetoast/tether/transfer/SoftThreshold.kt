package com.tubetoast.tether.transfer

object SoftThreshold {
    const val FILE_COUNT = 500
    val SIZE_BYTES = 2L * 1024 * 1024 * 1024

    fun exceeds(fileCount: Int, totalBytes: Long): Boolean =
        fileCount > FILE_COUNT || totalBytes > SIZE_BYTES
}
