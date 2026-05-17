package com.tubetoast.tether.presentation.transfer

import kotlin.math.roundToInt

private const val KB = 1_024L
private const val MB = 1_024L * KB
private const val GB = 1_024L * MB

fun formatBytes(bytes: Long): String = when {
    bytes < KB -> "$bytes B"
    bytes < MB -> "${oneDecimal(bytes.toDouble() / KB)} KB"
    bytes < GB -> "${oneDecimal(bytes.toDouble() / MB)} MB"
    else -> "${oneDecimal(bytes.toDouble() / GB)} GB"
}

fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"

fun formatProgress(bytesDone: Long, bytesTotal: Long?, speedBytesPerSec: Long): String {
    val done = formatBytes(bytesDone)
    val speed = formatSpeed(speedBytesPerSec)
    return if (bytesTotal != null) {
        val total = formatBytes(bytesTotal)
        "$done of $total · $speed"
    } else {
        "$done · $speed"
    }
}

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac"
}
