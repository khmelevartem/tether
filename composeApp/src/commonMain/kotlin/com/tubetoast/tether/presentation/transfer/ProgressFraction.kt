package com.tubetoast.tether.presentation.transfer

/**
 * Clamped progress fraction for byte counters. Returns `null` when [total] is unknown or
 * non-positive — the caller decides what an unknown fraction means (indeterminate bar, zero, …).
 */
fun Long.fractionOf(total: Long?): Float? =
    if (total != null && total > 0L) (toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
