package com.tubetoast.tether

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.SafFileSource

fun Intent.toFileSources(contentResolver: ContentResolver): List<FileSource> = when (action) {
    Intent.ACTION_SEND -> {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        uri
            ?.let { takePersistableAndWrap(it, contentResolver) }
            ?.let { listOf(it) }
            ?: emptyList()
    }

    Intent.ACTION_SEND_MULTIPLE -> {
        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        uris?.mapNotNull { uri ->
            takePersistableAndWrap(uri, contentResolver)
        } ?: emptyList()
    }

    else -> emptyList()
}

private fun Intent.takePersistableAndWrap(uri: Uri, contentResolver: ContentResolver): SafFileSource? = try {
    contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
    SafFileSource(uri, contentResolver)
} catch (_: SecurityException) {
    SafFileSource(uri, contentResolver)
}
