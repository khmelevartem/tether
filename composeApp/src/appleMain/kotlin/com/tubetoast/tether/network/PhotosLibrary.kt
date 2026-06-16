package com.tubetoast.tether.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.coroutines.resume

private val log = KydraLog.withTag(default = "Tether.PhotosSave")

/** Maps [PHAuthorizationStatus] to a K/N-friendly enum so tests don't depend on PhotoKit constants. */
internal enum class PhotosAuthStatus { Authorized, Limited, NotDetermined, Denied, Restricted }

/**
 * PhotoKit surface required by [PhotosUploadStorageDecorator]. Extracted so the orchestration
 * (gate order, de-dup, fallback) is testable without the headless K/N runner's absent PhotoKit.
 */
internal interface PhotosLibrary {
    fun addOnlyAuthStatus(): PhotosAuthStatus

    suspend fun requestAddOnlyAuth(): Boolean

    suspend fun save(path: String, mediaType: MediaType): Boolean
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal object RealPhotosLibrary : PhotosLibrary {
    override fun addOnlyAuthStatus(): PhotosAuthStatus =
        when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)) {
            PHAuthorizationStatusAuthorized -> PhotosAuthStatus.Authorized
            PHAuthorizationStatusLimited -> PhotosAuthStatus.Limited
            PHAuthorizationStatusNotDetermined -> PhotosAuthStatus.NotDetermined
            PHAuthorizationStatusDenied -> PhotosAuthStatus.Denied
            else -> PhotosAuthStatus.Restricted
        }

    override suspend fun requestAddOnlyAuth(): Boolean =
        suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { newStatus ->
                val granted = newStatus == PHAuthorizationStatusAuthorized ||
                    newStatus == PHAuthorizationStatusLimited
                cont.resume(granted)
            }
        }

    override suspend fun save(path: String, mediaType: MediaType): Boolean {
        val url = NSURL.fileURLWithPath(path)
        return suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    when (mediaType) {
                        MediaType.Image ->
                            PHAssetCreationRequest.creationRequestForAssetFromImageAtFileURL(url)
                        MediaType.Video ->
                            PHAssetCreationRequest.creationRequestForAssetFromVideoAtFileURL(url)
                    }
                },
                completionHandler = { success, error ->
                    if (!success) {
                        log.warn {
                            "PhotosLibrary.save failed:" +
                                " domain=${error?.domain}" +
                                " code=${error?.code}" +
                                " desc=${error?.localizedDescription}"
                        }
                    }
                    cont.resume(success)
                },
            )
        }
    }
}
