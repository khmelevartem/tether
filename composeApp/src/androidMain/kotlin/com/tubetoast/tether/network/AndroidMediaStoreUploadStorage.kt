package com.tubetoast.tether.network

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import io.ktor.utils.io.ByteReadChannel

internal class AndroidMediaStoreUploadStorage(
    private val context: Context,
) : UploadStorage {
    override fun ensureRoot() {
        // MediaStore collections are managed by the OS; no mkdir needed.
    }

    override fun resolveDestination(fileName: String): String = fileName

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long {
        val (leafName, relativePath) = mediaStorePath(destination)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, leafName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("FileServer: MediaStore insert failed for '$destination'")
        var total = 0L
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                streamUploadBody(body) { buffer, n ->
                    output.write(buffer, 0, n)
                    total += n.toLong()
                }
                output.flush()
            } ?: error("FileServer: could not open output stream for '$destination'")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
        return total
    }

    override fun deleteIfExists(destination: String) {
        val (leafName, relativePath) = mediaStorePath(destination)
        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.delete(
            uri,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(leafName, "$relativePath/"),
        )
    }

    override fun logInfo(message: String) {
        Log.i("FileServer", message)
    }

    override fun logError(message: String) {
        Log.e("FileServer", message)
    }

    private fun mediaStorePath(destination: String): Pair<String, String> {
        val leafName = destination.substringAfterLast('/')
        val parentPath = destination.substringBeforeLast('/', "")
        val relativePath = if (parentPath.isEmpty()) "Download/Tether" else "Download/Tether/$parentPath"
        return leafName to relativePath
    }
}
