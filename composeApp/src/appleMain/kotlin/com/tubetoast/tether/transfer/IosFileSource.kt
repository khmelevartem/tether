@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.feof
import platform.posix.fopen
import platform.posix.fread
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "IosFileSource")

private const val READ_BUFFER_SIZE = 65_536

/**
 * @param securityScoped when true, calls startAccessingSecurityScopedResource before reading
 *   and stopAccessingSecurityScopedResource in close(). Photo picker results pass false because
 *   the URL is a temp copy that is not security-scoped.
 * @param onClose called in close() after security scope is released; used by photo-backed sources
 *   to delete their temp copy.
 */
internal class IosFileSource(
    private val url: NSURL,
    override val relativePath: String,
    override val sizeBytes: Long?,
    private val securityScoped: Boolean,
    private val onClose: (() -> Unit)? = null,
) : FileSource {
    override val name: String = relativePath.substringAfterLast('/')
    private var accessStarted = false
    private var readerJob: Job? = null
    private var closed = false

    override suspend fun openReadChannel(): ByteReadChannel {
        if (securityScoped) {
            url.startAccessingSecurityScopedResource()
            accessStarted = true
        }
        return try {
            buildReadChannel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnreadableSourceException) {
            throw e
        } catch (e: Exception) {
            throw UnreadableSourceException(name, e)
        }
    }

    private suspend fun buildReadChannel(): ByteReadChannel {
        val path = url.path
            ?: throw UnreadableSourceException(name, IllegalStateException("NSURL has no path: $url"))
        val file = withContext(Dispatchers.IO) {
            fopen(path, "rb") ?: throw UnreadableSourceException(name, IllegalStateException("fopen failed for $path"))
        }
        val channel = ByteChannel(autoFlush = true)
        val readerScope = CoroutineScope(currentCoroutineContext() + Dispatchers.IO)
        readerJob = readerScope.launch {
            try {
                memScoped {
                    val buf = allocArray<ByteVar>(READ_BUFFER_SIZE)
                    while (true) {
                        val n = fread(buf, 1u, READ_BUFFER_SIZE.toULong(), file).toInt()
                        if (n > 0) channel.writeFully(buf.readBytes(n))
                        if (n < READ_BUFFER_SIZE) {
                            if (feof(file) != 0) break
                            throw UnreadableSourceException(name, IllegalStateException("fread error for $path"))
                        }
                    }
                }
                channel.flushAndClose()
            } catch (e: CancellationException) {
                channel.cancel(e)
                throw e
            } catch (e: Exception) {
                val cause = if (e is UnreadableSourceException) e else UnreadableSourceException(name, e)
                channel.cancel(cause)
            } finally {
                fclose(file)
            }
        }
        return channel
    }

    override fun close() {
        if (closed) return
        closed = true
        readerJob?.cancel()
        if (accessStarted) {
            url.stopAccessingSecurityScopedResource()
            accessStarted = false
        }
        try {
            onClose?.invoke()
        } catch (e: Exception) {
            log.warn { "close callback failed for $name: ${e.message}" }
        }
    }
}

internal fun securityScopedFileSource(url: NSURL, relativePath: String, sizeBytes: Long?): IosFileSource =
    IosFileSource(url = url, relativePath = relativePath, sizeBytes = sizeBytes, securityScoped = true)

internal fun tempCopyFileSource(url: NSURL, relativePath: String, sizeBytes: Long?): IosFileSource =
    IosFileSource(
        url = url,
        relativePath = relativePath,
        sizeBytes = sizeBytes,
        securityScoped = false,
        onClose = {
            val path = url.path
            if (path != null) {
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            }
        },
    )
