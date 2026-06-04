package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build

internal object ShareIntentParser {
    fun parse(intent: Intent, contentResolver: ContentResolver): List<FileSource> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: return emptyList()
                listOf(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent
                        .getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
                        ?.filterIsInstance<Uri>()
                        ?.let { ArrayList(it) }
                } ?: return emptyList()
            }
            else -> return emptyList()
        }
        return uris.map { uri ->
            AndroidUriFileSource(uri, contentResolver, contentResolver.resolveDisplayName(uri))
        }
    }
}
