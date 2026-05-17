package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel

class FakeFileSource(
    override val name: String,
    private val content: ByteArray = ByteArray(0),
    override val size: Long? = content.size.toLong(),
    override val relativePath: String? = null,
) : FileSource {
    override suspend fun openReadChannel(): ByteReadChannel = ByteReadChannel(content)
}
