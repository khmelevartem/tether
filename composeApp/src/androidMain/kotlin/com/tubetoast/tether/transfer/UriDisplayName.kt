package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

internal fun ContentResolver.resolveDisplayName(uri: Uri): String =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        } ?: uri.lastPathSegment ?: "file"
