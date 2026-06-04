package com.tubetoast.tether.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "Tether.FilePicker")

class AndroidFilePicker(
    private val coordinator: AndroidPickerCoordinator,
    private val contentResolver: ContentResolver,
    private val appContext: Context,
) : FilePicker {
    override suspend fun pickFiles(): List<FileSource> {
        val deferred = coordinator.begin()
        val launched = coordinator.launchFiles() != null
        log.info { "pickFiles — launcher ${if (launched) "fired" else "ABSENT"}" }
        if (!launched) {
            deferred.completeExceptionally(IllegalStateException("AndroidFilePicker: no file launcher attached"))
        }
        return deferred.await()
    }

    override suspend fun pickFolder(): List<FileSource> {
        val deferred = coordinator.begin()
        val launched = coordinator.launchFolder() != null
        log.info { "pickFolder — launcher ${if (launched) "fired" else "ABSENT"}" }
        if (!launched) {
            deferred.completeExceptionally(IllegalStateException("AndroidFilePicker: no folder launcher attached"))
        }
        return deferred.await()
    }

    override suspend fun pickPhotos(): List<FileSource> {
        val deferred = coordinator.begin()
        val launched = coordinator.launchPhotos() != null
        log.info { "pickPhotos — launcher ${if (launched) "fired" else "ABSENT"}" }
        if (!launched) {
            deferred.completeExceptionally(IllegalStateException("AndroidFilePicker: no photos launcher attached"))
        }
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
