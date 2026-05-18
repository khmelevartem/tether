package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.FileSource

const val FILE_COUNT_THRESHOLD = 500
const val TOTAL_BYTES_THRESHOLD = 2L * 1_073_741_824L

fun exceedsThreshold(sources: List<FileSource>): Boolean {
    if (sources.size >= FILE_COUNT_THRESHOLD) return true
    val known = sources.mapNotNull { it.size }
    return known.sum() >= TOTAL_BYTES_THRESHOLD
}
