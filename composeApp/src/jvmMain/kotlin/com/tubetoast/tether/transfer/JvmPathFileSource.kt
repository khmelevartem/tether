package com.tubetoast.tether.transfer

import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class JvmPathFileSource(
    private val path: Path,
    relativePath: String = path.fileName.toString(),
) : FileSource {
    override val name: String = path.fileName.toString()
    override val relativePath: String = relativePath
    override val sizeBytes: Long? = if (path.exists()) Files.size(path) else null

    private var openStream: InputStream? = null

    override suspend fun openReadChannel() = if (!path.exists()) {
        throw UnreadableSourceException(name)
    } else {
        val stream = path.inputStream()
        openStream = stream
        stream.toByteReadChannel()
    }

    override fun close() {
        openStream?.close()
        openStream = null
    }
}
