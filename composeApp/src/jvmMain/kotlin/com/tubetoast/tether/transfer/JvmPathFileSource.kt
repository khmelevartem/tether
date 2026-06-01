package com.tubetoast.tether.transfer

import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class JvmPathFileSource(
    private val path: Path,
) : FileSource {
    override val name: String = path.fileName.toString()
    override val relativePath: String = name
    override val sizeBytes: Long? = if (path.exists()) Files.size(path) else null

    override suspend fun openReadChannel() = if (!path.exists()) {
        throw UnreadableSourceException(name)
    } else {
        path.inputStream().toByteReadChannel()
    }

    override fun close() = Unit
}
