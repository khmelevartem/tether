@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.transfer

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.NSURLIsSymbolicLinkKey
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.PATH_MAX
import platform.posix.realpath
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val log = KydraLog.withTag(default = "Tether.IosFilePicker")

internal class IosFilePicker(
    private val viewControllerProvider: () -> UIViewController?,
) : FilePicker {
    // Strong references: UIDocumentPickerViewController and PHPickerViewController hold
    // their delegates weakly; these fields keep the delegates alive until the pick completes.
    private var activeDocDelegate: NSObject? = null
    private var activePhotoDelegate: PhotoPickerDelegate? = null

    override suspend fun pickFiles(): List<FileSource> =
        presentFilePicker(contentTypes = listOf(UTTypeItem))

    override suspend fun pickFolder(): List<FileSource> {
        val folderUrls = presentFolderPicker()
        if (folderUrls.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            folderUrls.flatMap { walkFolderInternal(it) }
        }
    }

    override suspend fun pickPhotos(): List<FileSource> = presentPhotoPicker()

    private suspend fun presentFilePicker(contentTypes: List<UTType>): List<FileSource> {
        val deferred = CompletableDeferred<List<FileSource>>()
        withContext(Dispatchers.Main) {
            val vc = resolveRootViewController() ?: run {
                deferred.complete(emptyList())
                return@withContext
            }
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = contentTypes,
                asCopy = false,
            )
            picker.allowsMultipleSelection = true
            val delegate = FilePickerDelegate(deferred)
            activeDocDelegate = delegate
            picker.delegate = delegate
            vc.presentViewController(picker, animated = true, completion = null)
        }
        return try {
            deferred.await()
        } finally {
            activeDocDelegate = null
        }.also { sources ->
            log.info { "document picker resolved: ${sources.size} item(s)" }
        }
    }

    private suspend fun presentFolderPicker(): List<NSURL> {
        val deferred = CompletableDeferred<List<NSURL>>()
        withContext(Dispatchers.Main) {
            val vc = resolveRootViewController() ?: run {
                deferred.complete(emptyList())
                return@withContext
            }
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeFolder),
                asCopy = false,
            )
            picker.allowsMultipleSelection = true
            val delegate = FolderPickerDelegate(deferred)
            activeDocDelegate = delegate
            picker.delegate = delegate
            vc.presentViewController(picker, animated = true, completion = null)
        }
        return try {
            deferred.await()
        } finally {
            activeDocDelegate = null
        }.also { urls ->
            log.info { "folder picker resolved: ${urls.size} folder(s)" }
        }
    }

    private suspend fun presentPhotoPicker(): List<FileSource> {
        val deferred = CompletableDeferred<List<FileSource>>()
        withContext(Dispatchers.Main) {
            val vc = resolveRootViewController() ?: run {
                deferred.complete(emptyList())
                return@withContext
            }
            val config = PHPickerConfiguration()
            config.selectionLimit = 0
            config.filter = PHPickerFilter.anyFilterMatchingSubfilters(
                listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter),
            )
            val picker = PHPickerViewController(configuration = config)
            val delegate = PhotoPickerDelegate(deferred)
            activePhotoDelegate = delegate
            picker.delegate = delegate
            vc.presentViewController(picker, animated = true, completion = null)
        }
        return try {
            deferred.await()
        } finally {
            activePhotoDelegate = null
        }.also { sources ->
            log.info { "photo picker resolved: ${sources.size} item(s)" }
        }
    }

    private fun resolveRootViewController(): UIViewController? {
        val vc = viewControllerProvider()
        if (vc == null) log.warn { "rootViewController unavailable" }
        return vc
    }

    @OptIn(ExperimentalAtomicApi::class)
    internal fun walkFolderInternal(folderUrl: NSURL): List<FileSource> {
        // Start the security scope on the picker-vended folder URL before enumeration.
        // On a real device the file-provider grants access while the scope is held; closing
        // it early (or never starting it) causes enumeratorAtURL to return nothing silently.
        folderUrl.startAccessingSecurityScopedResource()
        val folderName = folderUrl.lastPathComponent ?: "folder"
        // realpath resolves symlinks so itemUrl.path and folderPath share the same root on
        // macOS/iOS simulator, where NSTemporaryDirectory() may return /var while the
        // enumerator resolves item paths via /private/var.
        val rawPath = folderUrl.path ?: run {
            folderUrl.stopAccessingSecurityScopedResource()
            return emptyList()
        }
        val resolvedFolderPath = realpathOf(rawPath) ?: rawPath
        val resolvedFolderUrl = NSURL.fileURLWithPath(resolvedFolderPath)
        val fm = NSFileManager.defaultManager
        val keys = listOf(NSURLIsDirectoryKey, NSURLIsSymbolicLinkKey, NSURLFileSizeKey)
        val enumerator = fm.enumeratorAtURL(
            url = resolvedFolderUrl,
            includingPropertiesForKeys = keys,
            options = platform.Foundation.NSDirectoryEnumerationSkipsPackageDescendants,
            errorHandler = { url, error ->
                log.warn { "folder walk error at ${url?.path}: ${error?.localizedDescription}" }
                true
            },
        ) ?: run {
            folderUrl.stopAccessingSecurityScopedResource()
            return emptyList()
        }

        val result = mutableListOf<IosFileSource>()
        while (true) {
            val itemUrl = enumerator.nextObject() as? NSURL ?: break
            val resourceValues = memScoped {
                val errorPtr = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
                itemUrl.resourceValuesForKeys(keys, errorPtr.ptr).also {
                    if (it == null) {
                        log.warn {
                            "resourceValues failed for ${itemUrl.path}: ${errorPtr.value?.localizedDescription}"
                        }
                    }
                }
            } ?: continue

            val isSymlink = (resourceValues[NSURLIsSymbolicLinkKey] as? NSNumber)?.boolValue == true
            if (isSymlink) continue
            val isDir = (resourceValues[NSURLIsDirectoryKey] as? NSNumber)?.boolValue == true
            if (isDir) continue

            val itemPath = itemUrl.path ?: continue
            val folderPath = resolvedFolderUrl.path ?: continue
            val relativeSuffix = itemPath.removePrefix(folderPath).trimStart('/')
            val relativePath = "$folderName/$relativeSuffix"
            // Child items are NOT independently security-scoped — the folder scope covers them.
            // securityScoped=false prevents double start/stop on individual child URLs.
            val source = IosFileSource(
                url = itemUrl,
                relativePath = relativePath,
                sizeBytes = (resourceValues[NSURLFileSizeKey] as? NSNumber)?.longLongValue,
                securityScoped = false,
                onClose = null,
            )
            if (!HiddenFileFilter.isVisible(source)) continue
            result += source
        }

        if (result.isEmpty()) {
            folderUrl.stopAccessingSecurityScopedResource()
            return emptyList()
        }

        // Ref-count the folder scope: release exactly once when the last child is closed.
        val remaining = AtomicInt(result.size)
        return result.map { child ->
            FolderChildFileSource(child, onLastClose = {
                if (remaining.addAndFetch(-1) == 0) {
                    folderUrl.stopAccessingSecurityScopedResource()
                }
            })
        }
    }
}

/**
 * Wraps a child [IosFileSource] from a folder walk. Delegates all reads to the wrapped source
 * and fires [onLastClose] exactly once on [close], allowing a ref-count caller to track
 * when all sibling children have been closed.
 */
private class FolderChildFileSource(
    private val delegate: IosFileSource,
    private val onLastClose: () -> Unit,
) : FileSource by delegate {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        delegate.close()
        onLastClose()
    }
}

private class FilePickerDelegate(
    private val deferred: CompletableDeferred<List<FileSource>>,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val sources = didPickDocumentsAtURLs.filterIsInstance<NSURL>().map { url ->
            securityScopedFileSource(
                url = url,
                relativePath = url.lastPathComponent ?: url.absoluteString ?: "",
                sizeBytes = fileSizeOf(url),
            )
        }
        deferred.complete(sources)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        deferred.complete(emptyList())
    }

    private fun fileSizeOf(url: NSURL): Long? = memScoped {
        val errorPtr = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val values = url.resourceValuesForKeys(listOf(NSURLFileSizeKey), errorPtr.ptr)
        (values?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue
    }
}

private class FolderPickerDelegate(
    private val deferred: CompletableDeferred<List<NSURL>>,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        deferred.complete(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        deferred.complete(emptyList())
    }
}

@OptIn(ExperimentalAtomicApi::class)
private class PhotoPickerDelegate(
    private val deferred: CompletableDeferred<List<FileSource>>,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            deferred.complete(emptyList())
            return
        }
        loadPhotoResults(results)
    }

    private fun loadPhotoResults(results: List<PHPickerResult>) {
        val lock = NSLock()
        val collected = mutableListOf<FileSource>()
        val remaining = AtomicInt(results.size)

        for (result in results) {
            val provider = result.itemProvider
            val typeId = if (provider.hasItemConformingToTypeIdentifier("public.movie")) {
                "public.movie"
            } else {
                "public.image"
            }
            val displayName = provider.suggestedName ?: NSUUID().UUIDString

            provider.loadFileRepresentationForTypeIdentifier(typeId) { url, error ->
                var source: FileSource? = null
                if (error != null || url == null) {
                    log.warn { "loadFileRepresentation failed for $displayName: ${error?.localizedDescription}" }
                } else {
                    // Copy before this handler returns — the OS deletes the temp file when the handler exits.
                    val ext = url.pathExtension?.let { ".$it" } ?: ""
                    val destPath = "${NSTemporaryDirectory()}tether-photo-${NSUUID().UUIDString}$ext"
                    val destUrl = NSURL.fileURLWithPath(destPath)
                    val copyError = memScoped {
                        val errorPtr = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
                        val copied = NSFileManager.defaultManager.copyItemAtURL(
                            url,
                            toURL = destUrl,
                            error = errorPtr.ptr,
                        )
                        if (copied) null else errorPtr.value
                    }
                    if (copyError == null) {
                        val sizeBytes = memScoped {
                            val errorPtr = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
                            val values = destUrl.resourceValuesForKeys(listOf(NSURLFileSizeKey), errorPtr.ptr)
                            (values?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue
                        }
                        source = tempCopyFileSource(
                            url = destUrl,
                            relativePath = "$displayName$ext",
                            sizeBytes = sizeBytes,
                        )
                    } else {
                        log.warn { "failed to copy temp photo for $displayName: ${copyError.localizedDescription}" }
                    }
                }
                lock.lock()
                try {
                    if (source != null) collected.add(source)
                } finally {
                    lock.unlock()
                }
                if (remaining.addAndFetch(-1) == 0) {
                    deferred.complete(collected.toList())
                }
            }
        }
    }
}

private fun realpathOf(path: String): String? = memScoped {
    val buf = allocArray<ByteVar>(PATH_MAX)
    val result = realpath(path, buf) ?: return null
    result.toKString()
}
