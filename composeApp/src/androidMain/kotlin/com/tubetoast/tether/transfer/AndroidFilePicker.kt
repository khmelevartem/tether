package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CompletableDeferred

class AndroidFilePicker(
    private val activity: ComponentActivity,
    private val contentResolver: ContentResolver,
) : FilePicker {
    private var pendingFiles: CompletableDeferred<List<Uri>>? = null
    private var pendingFolder: CompletableDeferred<Uri?>? = null

    private val filesLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            pendingFiles?.complete(uris ?: emptyList())
        }

    private val folderLauncher: ActivityResultLauncher<Uri?> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            pendingFolder?.complete(uri)
        }

    override suspend fun pickFiles(multi: Boolean): List<FileSource> {
        val deferred = CompletableDeferred<List<Uri>>()
        pendingFiles = deferred
        filesLauncher.launch(arrayOf("*/*"))
        val uris = deferred.await()
        return uris.map { SafFileSource(it, contentResolver) }
    }

    override suspend fun pickFolder(): List<FileSource> {
        val deferred = CompletableDeferred<Uri?>()
        pendingFolder = deferred
        folderLauncher.launch(null)
        val rootUri = deferred.await() ?: return emptyList()
        val rootDoc = DocumentFile.fromTreeUri(activity, rootUri)
        return walkDocumentTree(rootDoc, null)
    }

    private fun walkDocumentTree(
        dir: DocumentFile?,
        parentPath: String?,
    ): List<FileSource> {
        if (dir == null || !dir.isDirectory) return emptyList()
        val result = mutableListOf<FileSource>()
        for (child in dir.listFiles().orEmpty()) {
            val childName = child.name ?: continue
            if (isHidden(childName)) continue
            val childPath = if (parentPath != null) "$parentPath/$childName" else childName
            if (child.isDirectory) {
                result += walkDocumentTree(child, childPath)
            } else if (child.isFile) {
                result += SafFileSource(child.uri, contentResolver, relativePath = childPath)
            }
        }
        return result
    }
}
