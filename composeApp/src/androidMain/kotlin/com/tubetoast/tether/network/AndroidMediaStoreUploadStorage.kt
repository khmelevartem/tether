package com.tubetoast.tether.network

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import io.ktor.utils.io.ByteReadChannel
import java.io.File

internal class AndroidMediaStoreUploadStorage(
    private val context: Context,
) : UploadStorage {
    override fun ensureRoot() {
        if (Build.VERSION.SDK_INT < 29) {
            legacyRoot().mkdirs()
        }
        // MediaStore collections on API 29+ are managed by the OS; no mkdir needed.
    }

    override fun resolveDestination(fileName: String): String {
        if (Build.VERSION.SDK_INT < 29) {
            val root = legacyRoot()
            val dest = resolveDestinationFile(root, fileName)
            val canonical = dest.canonicalPath
            val rootCanonical = root.canonicalPath
            check(canonical.startsWith(rootCanonical + File.separator) || canonical == rootCanonical) {
                "FileServer: path escape attempt detected: $canonical"
            }
            dest.parentFile?.mkdirs()
            return canonical
        }
        // For MediaStore path (API 29+) the destination is the sanitized relative path;
        // actual file identity is managed via the content URI inside writeViaMediaStore.
        return fileName
    }

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long = if (Build.VERSION.SDK_INT >=
        29
    ) {
        writeViaMediaStore(body, destination)
    } else {
        writeViaFile(body, destination)
    }

    override fun deleteIfExists(destination: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            // Best-effort: query by display name + relative path and delete.
            val leafName = destination.substringAfterLast('/')
            val parentPath = destination.substringBeforeLast('/', "")
            val relativePath = if (parentPath.isEmpty()) "Download/Tether" else "Download/Tether/$parentPath"
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            context.contentResolver.delete(
                uri,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(leafName, "$relativePath/"),
            )
        } else {
            try {
                File(destination).delete()
            } catch (_: Exception) {
            }
        }
    }

    override fun logInfo(message: String) {
        Log.i("FileServer", message)
    }

    override fun logError(message: String) {
        Log.e("FileServer", message)
    }

    private suspend fun writeViaMediaStore(body: ByteReadChannel, fileName: String): Long {
        val leafName = fileName.substringAfterLast('/')
        val parentPath = fileName.substringBeforeLast('/', "")
        val relativePath = if (parentPath.isEmpty()) "Download/Tether" else "Download/Tether/$parentPath"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, leafName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("FileServer: MediaStore insert failed for '$fileName'")
        var total = 0L
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                streamUploadBody(body) { buffer, n ->
                    output.write(buffer, 0, n)
                    total += n.toLong()
                }
                output.flush()
            } ?: error("FileServer: could not open output stream for '$fileName'")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
        return total
    }

    private suspend fun writeViaFile(body: ByteReadChannel, destination: String): Long {
        var total = 0L
        File(destination).outputStream().use { output ->
            streamUploadBody(body) { buffer, n ->
                output.write(buffer, 0, n)
                total += n.toLong()
            }
            output.flush()
        }
        return total
    }

    private fun legacyRoot(): File =
        Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .resolve("Tether")
}
