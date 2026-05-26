package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel

interface FileSource {
    val name: String

    /** POSIX-style relative path for folder sends; equals [name] for single-file sends. */
    val relativePath: String

    /** `null` when the size is not known before opening (e.g. live streams). */
    val sizeBytes: Long?

    suspend fun openReadChannel(): ByteReadChannel

    fun close()
}
