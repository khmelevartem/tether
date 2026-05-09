@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

private const val BUFFER_SIZE = 64 * 1024

actual class FileServer(
    private val port: Int,
    downloadsDir: String? = null,
) {
    private val downloadsDir: String = downloadsDir ?: defaultDownloadsDir()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        ensureDirectory(downloadsDir)
        val srv = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json() }
            routing {
                get("/health") { call.respond(HttpStatusCode.OK, "Tether OK") }
                post("/upload") {
                    val rawName = call.request.queryParameters["name"]
                    if (rawName.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "missing 'name' query parameter"),
                        )
                        return@post
                    }
                    val fileName = stripPathComponents(rawName)
                    val destPath = resolveDestination(this@FileServer.downloadsDir, fileName)
                    var uploadComplete = false
                    try {
                        writeChannelToFile(call.receiveChannel(), destPath)
                        uploadComplete = true
                        call.respond(HttpStatusCode.OK, mapOf("savedPath" to destPath))
                    } catch (e: Exception) {
                        NSLog("FileServer: upload failed for '%s' — %s", fileName, e.message ?: "unknown")
                        try {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (e.message ?: "upload failed")),
                            )
                        } catch (_: Exception) {
                        }
                    } finally {
                        if (!uploadComplete) {
                            deleteIfExists(destPath)
                        }
                    }
                }
            }
        }.start(wait = false)
        server = srv
        return runBlocking { srv.engine.resolvedConnectors() }.first().port
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
    }
}

private fun defaultDownloadsDir(): String {
    val docs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String
        ?: error("FileServer: NSDocumentDirectory unavailable")
    return "$docs/Tether"
}

private fun ensureDirectory(path: String) {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(path)) {
        fm.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
}

private fun stripPathComponents(rawName: String): String =
    rawName.substringAfterLast('/').substringAfterLast('\\')

private fun resolveDestination(dir: String, fileName: String): String {
    val fm = NSFileManager.defaultManager
    val firstTry = "$dir/$fileName"
    if (!fm.fileExistsAtPath(firstTry)) return firstTry
    val ext = fileName.substringAfterLast('.', "")
    val base = if (ext.isEmpty()) fileName else fileName.removeSuffix(".$ext")
    var i = 1
    while (true) {
        val candidate = if (ext.isEmpty()) "$dir/${base}_$i" else "$dir/${base}_$i.$ext"
        if (!fm.fileExistsAtPath(candidate)) return candidate
        i++
    }
}

private fun deleteIfExists(path: String) {
    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(path)) {
        fm.removeItemAtPath(path, error = null)
    }
}

private suspend fun writeChannelToFile(input: ByteReadChannel, path: String) {
    val file = fopen(path, "wb")
        ?: error("FileServer: could not open '$path' for writing")
    try {
        val buffer = ByteArray(BUFFER_SIZE)
        while (!input.isClosedForRead) {
            val n = input.readAvailable(buffer, 0, buffer.size)
            if (n <= 0) break
            buffer.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, n.toULong(), file)
            }
        }
    } finally {
        fclose(file)
    }
}
