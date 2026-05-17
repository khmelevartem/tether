package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel

interface FileSource {
    val name: String
    val size: Long?
    val relativePath: String?

    suspend fun openReadChannel(): ByteReadChannel
}
