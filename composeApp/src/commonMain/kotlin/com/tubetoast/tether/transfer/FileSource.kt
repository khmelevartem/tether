package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel

interface FileSource {
    val name: String

    /** POSIX-style relative path for folder sends; equals [name] for single-file sends. */
    val relativePath: String

    /** `null` when the size is not known before opening (e.g. live streams). */
    val sizeBytes: Long?

    /**
     * True when [openReadChannel] does significant work (e.g. exporting a photo from the gallery)
     * before any bytes flow. The sender surfaces a "preparing" phase for such sources so the gap
     * is not mistaken for a stalled transfer.
     */
    val materializesLazily: Boolean get() = false

    suspend fun openReadChannel(): ByteReadChannel

    fun close()
}
