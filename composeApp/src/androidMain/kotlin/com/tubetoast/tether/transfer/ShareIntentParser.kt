package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

internal object ShareIntentParser {
    fun parse(intent: Intent, contentResolver: ContentResolver): List<FileSource> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return emptyList()
                listOf(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("UNCHECKED_CAST")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return emptyList()
            }
            else -> return emptyList()
        }
        return uris.map { uri ->
            AndroidUriFileSource(uri, contentResolver, uri.lastPathSegment ?: "file")
        }
    }
}
