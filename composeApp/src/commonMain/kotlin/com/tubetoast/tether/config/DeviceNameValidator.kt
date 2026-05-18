package com.tubetoast.tether.config

internal object DeviceNameValidator {
    private const val MAX_CODE_POINTS = 50

    fun validate(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name must not be empty"))
        if (codePointCount(trimmed) > MAX_CODE_POINTS) {
            return Result.failure(IllegalArgumentException("Name must be at most $MAX_CODE_POINTS characters"))
        }
        return Result.success(trimmed)
    }

    private fun codePointCount(s: String): Int {
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
}
