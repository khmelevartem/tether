package com.tubetoast.tether.network

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.ktor.utils.io.ByteReadChannel
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "AndroidMediaStoreStorage")

internal class AndroidMediaStoreUploadStorage(
    private val contentResolver: ContentResolver,
) : UploadStorage {
    override fun ensureRoot() = Unit

    override fun resolveDestination(relativePath: String): UploadHandle {
        val leaf = relativePath.substringAfterLast('/', relativePath)
        val subDir = relativePath.substringBeforeLast('/', "")
        val mediaRelativePath = if (subDir.isEmpty()) {
            "${Environment.DIRECTORY_DOWNLOADS}/Tether"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/Tether/$subDir"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, leaf)
            put(MediaStore.Downloads.RELATIVE_PATH, mediaRelativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore: failed to insert row for '$relativePath'")
        log.info { "reserved MediaStore row for '$relativePath' → $uri" }
        return UploadHandle(destination = uri.toString(), createdDirs = emptyList())
    }

    override suspend fun writeBody(body: ByteReadChannel, handle: UploadHandle): Long {
        val uri = Uri.parse(handle.destination)
        val stream = contentResolver.openOutputStream(uri)
            ?: error("MediaStore: failed to open OutputStream for $uri")
        var bytesWritten = 0L
        stream.use { output ->
            streamUploadBody(body) { buffer, length ->
                output.write(buffer, 0, length)
                bytesWritten += length
            }
        }
        val updated = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        contentResolver.update(uri, updated, null, null)
        log.info { "wrote $bytesWritten bytes → $uri" }
        return bytesWritten
    }

    override fun abort(handle: UploadHandle) {
        val uri = Uri.parse(handle.destination)
        contentResolver.delete(uri, null, null)
        log.error { "aborted upload, deleted MediaStore row $uri" }
    }
}
