package com.tubetoast.tether.config

internal const val DEVICE_NAME_MAX_CODEPOINTS = 50

internal fun deviceNameCodepointCount(s: String): Int {
    var count = 0
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
            i += 2
        } else {
            i++
        }
        count++
    }
    return count
}

internal object DeviceNameValidator {
    fun validate(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name must not be empty"))
        if (deviceNameCodepointCount(trimmed) > DEVICE_NAME_MAX_CODEPOINTS) {
            return Result.failure(
                IllegalArgumentException("Name must be at most $DEVICE_NAME_MAX_CODEPOINTS characters"),
            )
        }
        return Result.success(trimmed)
    }
}
