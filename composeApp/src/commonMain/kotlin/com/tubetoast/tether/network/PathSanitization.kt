package com.tubetoast.tether.network

private val DRIVE_LETTER_RE = Regex("^[A-Za-z]:")
private const val MAX_PATH_LENGTH = 1024

internal fun sanitizeRelativePath(raw: String): String {
    val normalized = raw
        .replace('\\', '/')
        .replace(Regex("/+"), "/")
        .trimStart('/')

    if (DRIVE_LETTER_RE.containsMatchIn(normalized)) {
        return sanitizeRelativePath(normalized.substringAfter('/').ifEmpty { "_" })
    }

    val sanitized = normalized
        .split('/')
        .filter { it.isNotEmpty() && it != ".." }
        .joinToString("/")
        .take(MAX_PATH_LENGTH)

    return sanitized.ifEmpty { "_" }
}
