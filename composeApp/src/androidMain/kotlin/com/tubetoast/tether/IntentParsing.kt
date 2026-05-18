package com.tubetoast.tether

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.SafFileSource

private const val TAG = "IntentParsing"

fun Intent.toFileSources(contentResolver: ContentResolver): List<FileSource> = when (action) {
    Intent.ACTION_SEND -> {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            // Typed overload (API 33+) not available on API 29–32; non-typed form is the only option.
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
            // Typed overload (API 33+) not available on API 29–32; non-typed form is the only option.
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
} catch (e: SecurityException) {
    // If permission is not persistable (e.g. the activity was backgrounded before transfer start),
    // openReadChannel will fail later — the file will surface as Unreadable in the transfer summary.
    Log.w(TAG, "Couldn't take persistable URI permission for $uri", e)
    SafFileSource(uri, contentResolver)
}
