package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

suspend fun FileClient.send(device: Device, file: Path): SendResult {
    if (!file.exists()) throw FileNotFoundException(file.toString())
    return send(device, file.inputStream().toByteReadChannel(), file.fileName.toString())
}
