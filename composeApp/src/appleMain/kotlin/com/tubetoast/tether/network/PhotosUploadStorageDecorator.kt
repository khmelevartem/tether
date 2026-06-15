@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.UniformTypeIdentifiers.UTType
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.coroutines.resume

private val log = KydraLog.withTag(default = "Tether.PhotosSave")

/**
 * Wraps the iOS [UploadStorage] to copy received media into the Photos library after each
 * successful write. [resolveDestination] and [abort] delegate straight through.
 *
 * The Photos copy runs in [backgroundScope] after [writeBody] returns so the HTTP response
 * is never held open while an OS prompt is up. A Photos-copy failure never propagates as a
 * transfer failure — the received file in Documents/Tether is always preserved.
 *
 * @param mediaClassifier maps a file-path extension to [MediaType], or null if not media.
 *   Defaults to the UTType-based implementation; override in tests to avoid the live
 *   UTType database which is absent in the headless KN test runner.
 */
internal class PhotosUploadStorageDecorator(
    private val delegate: UploadStorage,
    private val saveToGallery: suspend () -> Boolean,
    private val backgroundScope: CoroutineScope,
    private val mediaClassifier: (ext: String) -> MediaType? = ::classifyByUTType,
) : UploadStorage {
    private val authMutex = Mutex()

    /**
     * Non-null while an iOS authorization prompt is in flight.
     * Guards a single prompt for concurrent media arrivals — others await the same deferred.
     */
    private var pendingAuth: CompletableDeferred<Boolean>? = null

    override fun ensureRoot() = delegate.ensureRoot()

    override fun resolveDestination(relativePath: String): UploadHandle =
        delegate.resolveDestination(relativePath)

    override suspend fun writeBody(body: ByteReadChannel, handle: UploadHandle): Long {
        val bytesWritten = delegate.writeBody(body, handle)
        val destination = handle.destination
        backgroundScope.launch {
            trySaveToPhotos(destination)
        }
        return bytesWritten
    }

    override fun abort(handle: UploadHandle) = delegate.abort(handle)

    private suspend fun trySaveToPhotos(destination: String) {
        if (!saveToGallery()) return
        val mediaType = detectMediaType(destination) ?: return
        if (!resolveAuthorization()) return
        saveFile(destination, mediaType)
    }

    internal fun detectMediaType(path: String): MediaType? {
        val ext = path.substringAfterLast('.', missingDelimiterValue = "").ifEmpty { return null }
        return mediaClassifier(ext)
    }

    private suspend fun resolveAuthorization(): Boolean {
        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
        return when (status) {
            PHAuthorizationStatusAuthorized, PHAuthorizationStatusLimited -> true
            PHAuthorizationStatusNotDetermined -> promptForAuthorization()
            PHAuthorizationStatusDenied -> {
                log.info { "Photos add-only permission denied — file stays in Files" }
                false
            }
            else -> {
                log.info { "Photos authorization restricted — file stays in Files" }
                false
            }
        }
    }

    /**
     * Surfaces iOS's own add-to-Photos prompt exactly once regardless of how many media files
     * arrive concurrently while status is NotDetermined.
     */
    private suspend fun promptForAuthorization(): Boolean {
        authMutex.withLock {
            val existing = pendingAuth
            if (existing != null) {
                // Another coroutine already launched the prompt — piggyback on it.
                return existing.await()
            }
            pendingAuth = CompletableDeferred()
        }
        // This coroutine owns the prompt.
        val result = suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { newStatus ->
                val granted = newStatus == PHAuthorizationStatusAuthorized ||
                    newStatus == PHAuthorizationStatusLimited
                cont.resume(granted)
            }
        }
        authMutex.withLock {
            pendingAuth!!.complete(result)
            pendingAuth = null
        }
        if (result) {
            log.info { "Photos add-only permission granted" }
        } else {
            log.info { "Photos add-only permission denied after prompt — file stays in Files" }
        }
        return result
    }

    private suspend fun saveFile(path: String, mediaType: MediaType) {
        val url = NSURL.fileURLWithPath(path)
        val (success, error) = suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    when (mediaType) {
                        MediaType.Image ->
                            PHAssetCreationRequest.creationRequestForAssetFromImageAtFileURL(url)
                                ?: log.warn { "Photos: unsupported image format for $path — file stays in Files" }
                        MediaType.Video ->
                            PHAssetCreationRequest.creationRequestForAssetFromVideoAtFileURL(url)
                                ?: log.warn { "Photos: unsupported video format for $path — file stays in Files" }
                    }
                },
                completionHandler = { success, error ->
                    cont.resume(Pair(success, error))
                },
            )
        }
        if (success) {
            log.info { "Photos: saved ${path.substringAfterLast('/')}" }
        } else {
            log.warn {
                "Photos: save failed for ${path.substringAfterLast('/')} — " +
                    (error?.localizedDescription ?: "unknown error") + " — file stays in Files"
            }
        }
    }
}

internal enum class MediaType { Image, Video }

/**
 * Maps a filename extension to [MediaType] using UTType conformance.
 *
 * Uses typeWithIdentifier: to construct the reference types instead of the NS_REFINED_FOR_SWIFT
 * global constants (UTTypeImage, UTTypeMovie) which are inaccessible from K/N platform stubs.
 */
internal fun classifyByUTType(ext: String): MediaType? {
    val imageType = UTType.typeWithIdentifier("public.image") ?: return null
    val movieType = UTType.typeWithIdentifier("public.movie") ?: return null
    return when {
        UTType.typeWithFilenameExtension(ext, conformingToType = imageType) != null -> MediaType.Image
        UTType.typeWithFilenameExtension(ext, conformingToType = movieType) != null -> MediaType.Video
        else -> null
    }
}
