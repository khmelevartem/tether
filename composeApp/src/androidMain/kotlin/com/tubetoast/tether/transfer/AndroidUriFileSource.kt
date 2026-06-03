package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "AndroidUriFileSource")

internal class AndroidUriFileSource(
    private val uri: Uri,
    private val contentResolver: ContentResolver,
    override val relativePath: String,
) : FileSource {
    override val name: String = relativePath.substringAfterLast('/')

    override val sizeBytes: Long? by lazy { querySize() }

    private fun querySize(): Long? =
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) cursor.getLong(idx) else null
            } else {
                null
            }
        }

    override suspend fun openReadChannel(): ByteReadChannel = withContext(Dispatchers.IO) {
        tryTakePersistablePermission()
        val stream = contentResolver.openInputStream(uri)
            ?: error("ContentResolver returned null InputStream for $uri")
        stream.toByteReadChannel()
    }

    private fun tryTakePersistablePermission() {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            log.warn { e withMessage "takePersistableUriPermission failed for $uri — using session-scoped access" }
        }
    }

    override fun close() = Unit
}
