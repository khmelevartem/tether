package com.tubetoast.tether.transfer

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import kotlinx.coroutines.CompletableDeferred

/**
 * Retains the in-flight pick deferred and launcher references across Activity recreation.
 * Must be container-scoped (not Activity-scoped) for rotation survival. If a new pick starts
 * while one is already in flight, the prior deferred is cancelled.
 *
 * Launchers must be set (via [updateLaunchers]) before any pick is triggered.
 * [launchFiles], [launchFolder], [launchPhotos] fail the in-flight deferred immediately when
 * no launcher is attached.
 */
class AndroidPickerCoordinator {
    private var filesLauncher: ActivityResultLauncher<Array<String>>? = null
    private var folderLauncher: ActivityResultLauncher<Uri?>? = null
    private var photosLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null

    private var inFlight: CompletableDeferred<List<FileSource>>? = null

    @Synchronized
    fun updateLaunchers(
        files: ActivityResultLauncher<Array<String>>,
        folder: ActivityResultLauncher<Uri?>,
        photos: ActivityResultLauncher<PickVisualMediaRequest>,
    ) {
        filesLauncher = files
        folderLauncher = folder
        photosLauncher = photos
    }

    @Synchronized
    fun begin(): CompletableDeferred<List<FileSource>> {
        inFlight?.cancel()
        val deferred = CompletableDeferred<List<FileSource>>()
        inFlight = deferred
        return deferred
    }

    @Synchronized
    fun resolve(sources: List<FileSource>) {
        inFlight?.complete(sources)
        inFlight = null
    }

    @Synchronized
    fun launchFiles() {
        filesLauncher?.launch(arrayOf("*/*"))
            ?: inFlight?.completeExceptionally(
                IllegalStateException("AndroidPickerCoordinator: no files launcher attached"),
            )
    }

    @Synchronized
    fun launchFolder() {
        folderLauncher?.launch(null)
            ?: inFlight?.completeExceptionally(
                IllegalStateException("AndroidPickerCoordinator: no folder launcher attached"),
            )
    }

    @Synchronized
    fun launchPhotos() {
        photosLauncher?.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
            ?: inFlight?.completeExceptionally(
                IllegalStateException("AndroidPickerCoordinator: no photos launcher attached"),
            )
    }
}
