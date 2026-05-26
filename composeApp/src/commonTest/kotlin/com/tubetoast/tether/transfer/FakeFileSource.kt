package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel

internal class FakeFileSource(
    override val name: String,
    override val sizeBytes: Long?,
    override val relativePath: String = name,
    val throwOnOpen: Throwable? = null,
) : FileSource {
    override suspend fun openReadChannel(): ByteReadChannel {
        throwOnOpen?.let { throw it }
        return ByteReadChannel(ByteArray(sizeBytes?.toInt() ?: 0))
    }

    override fun close() = Unit
}
