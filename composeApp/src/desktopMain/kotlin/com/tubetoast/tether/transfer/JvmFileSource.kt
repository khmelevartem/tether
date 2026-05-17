package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.nio.file.Files
import java.nio.file.Path

class JvmFileSource(
    private val path: Path,
    override val relativePath: String? = null,
) : FileSource {
    override val name: String get() = path.fileName.toString()
    override val size: Long? get() = runCatching { Files.size(path) }.getOrNull()

    override suspend fun openReadChannel(): ByteReadChannel =
        path.toFile().inputStream().toByteReadChannel()
}
