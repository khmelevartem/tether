package com.tubetoast.tether.util

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "${formatOneDecimal(bytes / 1_073_741_824.0)} GB"
    bytes >= 1_048_576L -> "${formatOneDecimal(bytes / 1_048_576.0)} MB"
    bytes >= 1_024L -> "${formatOneDecimal(bytes / 1_024.0)} KB"
    else -> "$bytes B"
}

private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10).toInt()
    val whole = rounded / 10
    val frac = rounded % 10
    return "$whole.$frac"
}
