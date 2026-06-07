package com.tubetoast.tether.config

enum class DeviceNameViolation { Empty, TooLong }

private const val DEVICE_NAME_MAX_CODEPOINTS = 50

private fun deviceNameCodepointCount(s: String): Int {
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
    fun violationOf(raw: String): DeviceNameViolation? {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> DeviceNameViolation.Empty
            deviceNameCodepointCount(trimmed) > DEVICE_NAME_MAX_CODEPOINTS -> DeviceNameViolation.TooLong
            else -> null
        }
    }

    fun validate(raw: String): Result<String> {
        val trimmed = raw.trim()
        return when (violationOf(raw)) {
            DeviceNameViolation.Empty -> Result.failure(IllegalArgumentException("Name must not be empty"))
            DeviceNameViolation.TooLong -> Result.failure(
                IllegalArgumentException("Name must be at most $DEVICE_NAME_MAX_CODEPOINTS characters"),
            )
            null -> Result.success(trimmed)
        }
    }
}
