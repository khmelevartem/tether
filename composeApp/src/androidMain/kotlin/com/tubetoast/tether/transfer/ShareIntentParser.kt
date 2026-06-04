package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

internal object ShareIntentParser {
    fun parse(intent: Intent, contentResolver: ContentResolver): List<FileSource> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return emptyList()
                listOf(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent
                    .getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
                    ?.filterIsInstance<Uri>()
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        return uris.map { uri ->
            AndroidUriFileSource(uri, contentResolver, resolveDisplayName(uri, contentResolver))
        }
    }

    private fun resolveDisplayName(uri: Uri, contentResolver: ContentResolver): String =
        contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            } ?: uri.lastPathSegment ?: "file"
}
