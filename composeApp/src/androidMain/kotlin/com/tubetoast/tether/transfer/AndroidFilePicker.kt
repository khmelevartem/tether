package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.documentfile.provider.DocumentFile
import com.tubetoast.tether.di.ActivityProvider

class AndroidFilePicker(
    private val activityProvider: ActivityProvider,
    val coordinator: AndroidPickerCoordinator,
    private val contentResolver: ContentResolver,
) : FilePicker {
    private var filesLauncher: ActivityResultLauncher<Array<String>>? = null
    private var folderLauncher: ActivityResultLauncher<Uri?>? = null
    private var photosLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null

    fun attach(
        files: ActivityResultLauncher<Array<String>>,
        folder: ActivityResultLauncher<Uri?>,
        photos: ActivityResultLauncher<PickVisualMediaRequest>,
    ) {
        filesLauncher = files
        folderLauncher = folder
        photosLauncher = photos
    }

    override suspend fun pickFiles(): List<FileSource> {
        checkNotNull(activityProvider.current) { "AndroidFilePicker: no Activity resumed" }
        val deferred = coordinator.begin()
        filesLauncher?.launch(arrayOf("*/*"))
        return deferred.await()
    }

    override suspend fun pickFolder(): List<FileSource> {
        checkNotNull(activityProvider.current) { "AndroidFilePicker: no Activity resumed" }
        val deferred = coordinator.begin()
        folderLauncher?.launch(null)
        return deferred.await()
    }

    override suspend fun pickPhotos(): List<FileSource> {
        checkNotNull(activityProvider.current) { "AndroidFilePicker: no Activity resumed" }
        val deferred = coordinator.begin()
        photosLauncher?.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        return deferred.await()
    }

    fun resolveUris(uris: List<Uri>): List<FileSource> =
        uris.map { uri ->
            AndroidUriFileSource(uri, contentResolver, uri.lastPathSegment ?: "file")
        }

    fun resolveTree(treeUri: Uri): List<FileSource> {
        val activity = checkNotNull(activityProvider.current) { "AndroidFilePicker: no Activity resumed" }
        val root = DocumentFile.fromTreeUri(activity, treeUri) ?: return emptyList()
        return collectVisible(root, "")
    }

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
