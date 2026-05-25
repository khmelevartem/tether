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

    // Decodes percent-escapes as UTF-8 byte sequences. Non-ASCII codepoints
    // arrive percent-encoded over HTTP as multiple `%XX` bytes (one per UTF-8
    // octet); we collect consecutive escape bytes and decode them as a batch
    // so multi-byte codepoints reassemble correctly. Literal characters in
    // the input are appended as-is, preserving any surrogate pairs.
    private fun urlDecode(input: String): String? {
        val out = StringBuilder(input.length)
        val pendingBytes = mutableListOf<Byte>()

        fun flushBytes() {
            if (pendingBytes.isNotEmpty()) {
                out.append(pendingBytes.toByteArray().decodeToString())
                pendingBytes.clear()
            }
        }
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%') {
                if (i + 2 >= input.length) return null
                val hex = input.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16) ?: return null
                pendingBytes.add(code.toByte())
                i += 3
            } else {
                flushBytes()
                out.append(c)
                i++
            }
        }
        flushBytes()
        return out.toString()
    }
}
