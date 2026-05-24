package com.tubetoast.tether.network

internal object PathSanitization {
    // Decode exactly once — repeated decodes would normalise double-encoded
    // inputs the sender has no legitimate use for.
    fun sanitizeRelativePath(raw: String): String? {
        if (raw.isEmpty()) return null

        val decoded = urlDecode(raw) ?: return null

        if (decoded.contains('\u0000')) return null

        val normalised = decoded.replace('\\', '/')

        if (normalised.startsWith('/')) return null
        if (driveLetterPrefix.containsMatchIn(normalised)) return null

        // Empty segments are rejected rather than silently collapsed —
        // they indicate sender confusion, not a recoverable case.
        val segments = normalised.split('/')
        for (segment in segments) {
            if (segment.isEmpty()) return null
            if (segment == "." || segment == "..") return null
        }

        return segments.joinToString("/")
    }

    private val driveLetterPrefix = Regex("^[A-Za-z]:")

    private fun urlDecode(input: String): String? {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%') {
                if (i + 2 >= input.length) return null
                val hex = input.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16) ?: return null
                sb.append(code.toChar())
                i += 3
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
