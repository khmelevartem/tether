package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFilePicker(
    private val coordinator: AndroidPickerCoordinator,
    private val contentResolver: ContentResolver,
    private val appContext: Context,
) : FilePicker {
    override suspend fun pickFiles(): List<FileSource> {
        val deferred = coordinator.begin()
        coordinator.launchFiles()
        return deferred.await()
    }

    override suspend fun pickFolder(): List<FileSource> {
        val deferred = coordinator.begin()
        coordinator.launchFolder()
        return deferred.await()
    }

    override suspend fun pickPhotos(): List<FileSource> {
        val deferred = coordinator.begin()
        coordinator.launchPhotos()
        return deferred.await()
    }

    fun resolveUris(uris: List<Uri>): List<FileSource> =
        uris.map { uri -> AndroidUriFileSource(uri, contentResolver, contentResolver.resolveDisplayName(uri)) }

    suspend fun resolveTree(treeUri: Uri): List<FileSource> {
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return emptyList()
        return withContext(Dispatchers.IO) { collectVisible(root, "") }
    }

    private fun collectVisible(dir: DocumentFile, prefix: String): List<FileSource> {
        val result = mutableListOf<FileSource>()
        for (file in dir.listFiles()) {
            val entryName = file.name ?: continue
            val relativePath = if (prefix.isEmpty()) entryName else "$prefix/$entryName"
            val source = AndroidUriFileSource(file.uri, contentResolver, relativePath)
            if (!HiddenFileFilter.isVisible(source)) continue
            if (file.isDirectory) {
                result += collectVisible(file, relativePath)
            } else {
                result += source
            }
        }
        return result
    }
}
