package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

suspend fun FileClient.send(
    device: Device,
    file: Path,
    onProgress: ((bytesTransferred: Long, totalBytes: Long?) -> Unit)? = null,
): SendResult {
    if (!file.exists()) throw FileNotFoundException(file.toString())
    return send(
        device = device,
        channel = file.inputStream().toByteReadChannel(),
        fileName = file.fileName.toString(),
        totalBytes = Files.size(file),
        onProgress = onProgress,
    )
}
