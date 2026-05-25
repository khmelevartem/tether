package com.tubetoast.tether.network

import kotlin.text.CharacterCodingException

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

        val segments = normalised.split('/')
        for (segment in segments) {
            if (segment.isEmpty()) return null
            if (segment == "." || segment == "..") return null
        }

        return segments.joinToString("/")
    }

    private val driveLetterPrefix = Regex("^[A-Za-z]:")

    // Non-ASCII codepoints arrive percent-encoded over HTTP as multiple `%XX`
    // bytes (one per UTF-8 octet); consecutive escape bytes are collected and
    // decoded as a batch so multi-byte codepoints reassemble correctly.
    // Literal characters are appended as-is, preserving any surrogate pairs.
    private fun urlDecode(input: String): String? {
        val out = StringBuilder(input.length)
        val pendingBytes = mutableListOf<Byte>()

        // Strict UTF-8: malformed sequences (lone continuation byte, truncated
        // multi-byte, overlong encodings) are rejected outright rather than
        // silently replaced with U+FFFD, which would let an attacker hide
        // bytes from the segment checks.
        var malformed = false

        fun flushBytes() {
            if (pendingBytes.isNotEmpty()) {
                try {
                    out.append(pendingBytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
                } catch (_: CharacterCodingException) {
                    malformed = true
                }
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
        if (malformed) return null
        return out.toString()
    }
}
