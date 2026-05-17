package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.tubetoast.tether.transfer.FileSource
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel

class SafFileSource(
    private val uri: Uri,
    private val contentResolver: ContentResolver,
    override val relativePath: String? = null,
) : FileSource {
    override val name: String by lazy { resolveName() }
    override val size: Long? by lazy { resolveSize() }

    override suspend fun openReadChannel(): ByteReadChannel =
        contentResolver.openInputStream(uri)!!.toByteReadChannel()

    private fun resolveName(): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                return cursor.getString(nameIndex) ?: uri.lastPathSegment ?: "file"
            }
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun resolveSize(): Long? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                return cursor.getLong(sizeIndex)
            }
        }
        return null
    }
}
