@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.UniformTypeIdentifiers.UTType
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "Tether.PhotosSave")

/**
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
    private val photosLibrary: PhotosLibrary = RealPhotosLibrary,
) : UploadStorage {
    private val authMutex = Mutex()

    /**
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
            try {
                trySaveToPhotos(destination)
            } catch (e: Exception) {
                log.warn { "Photos copy failed — file stays in Files: ${e.message}" }
            }
        }
        return bytesWritten
    }

    override fun abort(handle: UploadHandle) = delegate.abort(handle)

    private suspend fun trySaveToPhotos(destination: String) {
        if (!saveToGallery()) return
        val mediaType = detectMediaType(destination) ?: return
        if (!resolveAuthorization()) return
        val success = photosLibrary.save(destination, mediaType)
        if (success) {
            log.info { "Photos: saved ${destination.substringAfterLast('/')}" }
        } else {
            log.warn {
                "Photos: save failed for ${destination.substringAfterLast('/')} — file stays in Files"
            }
        }
    }

    internal fun detectMediaType(path: String): MediaType? {
        val ext = path.substringAfterLast('.', missingDelimiterValue = "").ifEmpty { return null }
        return mediaClassifier(ext)
    }

    private suspend fun resolveAuthorization(): Boolean =
        when (photosLibrary.addOnlyAuthStatus()) {
            PhotosAuthStatus.Authorized, PhotosAuthStatus.Limited -> true
            PhotosAuthStatus.NotDetermined -> promptForAuthorization()
            PhotosAuthStatus.Denied -> {
                log.info { "Photos add-only permission denied — file stays in Files" }
                false
            }
            PhotosAuthStatus.Restricted -> {
                log.info { "Photos authorization restricted — file stays in Files" }
                false
            }
        }

    /**
     * Deduplicates the OS add-to-Photos prompt so concurrent media arrivals share one prompt
     * and the same result.
     */
    private suspend fun promptForAuthorization(): Boolean {
        val existing = authMutex.withLock {
            pendingAuth.also { if (it == null) pendingAuth = CompletableDeferred() }
        }
        if (existing != null) return existing.await()

        // This coroutine owns the prompt.
        val result = photosLibrary.requestAddOnlyAuth()
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
}

internal enum class MediaType { Image, Video }

/**
 * Uses typeWithIdentifier: to construct the reference types instead of the NS_REFINED_FOR_SWIFT
 * global constants (UTTypeImage, UTTypeMovie) which are inaccessible from K/N platform stubs.
 */
@OptIn(kotlinx.cinterop.BetaInteropApi::class)
internal fun classifyByUTType(ext: String): MediaType? {
    val imageType = UTType.typeWithIdentifier("public.image") ?: return null
    val movieType = UTType.typeWithIdentifier("public.movie") ?: return null
    return when {
        UTType.typeWithFilenameExtension(ext, conformingToType = imageType) != null -> MediaType.Image
        UTType.typeWithFilenameExtension(ext, conformingToType = movieType) != null -> MediaType.Video
        else -> null
    }
}
