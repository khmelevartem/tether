package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.tubetoast.tether.di.ActivityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFilePicker(
    private val activityProvider: ActivityProvider,
    val coordinator: AndroidPickerCoordinator,
    private val contentResolver: ContentResolver,
) : FilePicker {
    override suspend fun pickFiles(): List<FileSource> {
        val deferred = coordinator.begin()
        if (coordinator.launchFiles() == null) {
            deferred.completeExceptionally(IllegalStateException("AndroidFilePicker: no file launcher attached"))
        }
        return deferred.await()
    }

    override suspend fun pickFolder(): List<FileSource> {
        val deferred = coordinator.begin()
        if (coordinator.launchFolder() == null) {
            deferred.completeExceptionally(IllegalStateException("AndroidFilePicker: no folder launcher attached"))
        }
        return deferred.await()
    }

    override suspend fun pickPhotos(): List<FileSource> {
        val deferred = coordinator.begin()
        if (coordinator.launchPhotos() == null) {
            deferred.completeExceptionally(IllegalStateException("AndroidFilePicker: no photos launcher attached"))
        }
        return deferred.await()
    }

    fun resolveUris(uris: List<Uri>): List<FileSource> =
        uris.map { uri -> AndroidUriFileSource(uri, contentResolver, resolveDisplayName(uri)) }

    suspend fun resolveTree(treeUri: Uri): List<FileSource> {
        val activity = checkNotNull(activityProvider.current) { "AndroidFilePicker: no Activity resumed" }
        val root = DocumentFile.fromTreeUri(activity, treeUri) ?: return emptyList()
        return withContext(Dispatchers.IO) { collectVisible(root, "") }
    }

    fun resolveDisplayName(uri: Uri): String =
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

    private fun collectVisible(dir: DocumentFile, prefix: String): List<FileSource> {
        val result = mutableListOf<FileSource>()
        for (file in dir.listFiles()) {
            val entryName = file.name ?: continue
            if (isHidden(entryName)) continue
            val relativePath = if (prefix.isEmpty()) entryName else "$prefix/$entryName"
            if (file.isDirectory) {
                result += collectVisible(file, relativePath)
            } else {
                result += AndroidUriFileSource(file.uri, contentResolver, relativePath)
            }
        }
        return result
    }

    private fun isHidden(name: String): Boolean =
        name.startsWith(".") || name in HIDDEN_NAMES

    private companion object {
        val HIDDEN_NAMES = setOf("Thumbs.db", ".DS_Store", "desktop.ini")
    }
}
