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
import platform.Foundation.NSItemProvider
import platform.Foundation.NSNumber
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

private val log = KydraLog.withTag(default = "IosFilePicker")

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

    private suspend fun presentFilePicker(contentTypes: List<UTType>): List<FileSource> =
        presentDocumentPicker(
            contentTypes = contentTypes,
            delegateFactory = ::FilePickerDelegate,
            empty = emptyList(),
            logLabel = { sources -> "document picker resolved: ${sources.size} item(s)" },
        )

    private suspend fun presentFolderPicker(): List<NSURL> =
        presentDocumentPicker(
            contentTypes = listOf(UTTypeFolder),
            delegateFactory = ::FolderPickerDelegate,
            empty = emptyList(),
            logLabel = { urls -> "folder picker resolved: ${urls.size} folder(s)" },
        )

    private suspend fun <T> presentDocumentPicker(
        contentTypes: List<UTType>,
        delegateFactory: (CompletableDeferred<T>) -> NSObject,
        empty: T,
        logLabel: (T) -> String,
    ): T {
        val deferred = CompletableDeferred<T>()
        withContext(Dispatchers.Main) {
            val vc = resolveRootViewController() ?: run {
                deferred.complete(empty)
                return@withContext
            }
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = contentTypes,
                asCopy = false,
            )
            picker.allowsMultipleSelection = true
            val delegate = delegateFactory(deferred)
            activeDocDelegate = delegate
            picker.delegate = delegate as? UIDocumentPickerDelegateProtocol
            log.info { "presenting document picker" }
            vc.presentViewController(picker, animated = true, completion = null)
        }
        return try {
            deferred.await()
        } finally {
            activeDocDelegate = null
        }.also { result ->
            log.info { logLabel(result) }
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
            log.info { "presenting photo picker" }
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
    internal fun walkFolderInternal(
        folderUrl: NSURL,
        onScopeReleased: () -> Unit = { folderUrl.stopAccessingSecurityScopedResource() },
    ): List<FileSource> {
        // Start the security scope on the picker-vended folder URL before enumeration.
        // On a real device the file-provider grants access while the scope is held; closing
        // it early (or never starting it) causes enumeratorAtURL to return nothing silently.
        folderUrl.startAccessingSecurityScopedResource()
        val folderName = folderUrl.lastPathComponent ?: "folder"
        // realpath resolves symlinks so itemUrl.path and folderPath share the same root on iOS
        // simulator and macOS, where NSTemporaryDirectory() may return /var while the enumerator
        // resolves item paths via /private/var. Real-device behavior is unverified.
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
                    onScopeReleased()
                }
            })
        }
    }
}

/**
 * Fires [onLastClose] exactly once on [close], regardless of how many times [close] is called.
 * This ensures the shared folder security scope is released exactly once when the last sibling is closed.
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

    private fun fileSizeOf(url: NSURL): Long? {
        // On a real device the OS withholds resource values (including size) until the security
        // scope is active — the same quirk as folder enumeration. Hold it just for the read, so the
        // size is known up front and the transfer shows byte progress instead of an indeterminate bar.
        val started = url.startAccessingSecurityScopedResource()
        return try {
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
                val values = url.resourceValuesForKeys(listOf(NSURLFileSizeKey), errorPtr.ptr)
                (values?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue
            }
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
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

private class PhotoPickerDelegate(
    private val deferred: CompletableDeferred<List<FileSource>>,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val sources = didFinishPicking
            .filterIsInstance<PHPickerResult>()
            .map { lazyPhotoSource(it.itemProvider) }
        deferred.complete(sources)
    }

    private fun lazyPhotoSource(provider: NSItemProvider): FileSource {
        // Load via the generic image/movie UTI: it is always file-representable, unlike a concrete
        // first registered identifier (e.g. a Live Photo bundle), which loadFileRepresentation
        // cannot export — that would surface the item as silently unreadable.
        val typeId = if (provider.hasItemConformingToTypeIdentifier("public.movie")) "public.movie" else "public.image"
        val baseName = provider.suggestedName ?: NSUUID().UUIDString
        val ext = predictedExtension(provider)
        val relativePath = when {
            ext == null -> baseName
            baseName.endsWith(".$ext", ignoreCase = true) -> baseName
            else -> "$baseName.$ext"
        }
        return LazyPhotoFileSource(provider, typeId, relativePath)
    }

    // suggestedName usually lacks an extension; derive one from the item's concrete registered UTIs
    // so the received file keeps a usable name. The materialized temp uses the real exported extension.
    private fun predictedExtension(provider: NSItemProvider): String? =
        provider.registeredTypeIdentifiers
            .filterIsInstance<String>()
            .firstNotNullOfOrNull { UTType.typeWithIdentifier(it)?.preferredFilenameExtension }
}

private fun realpathOf(path: String): String? = memScoped {
    val buf = allocArray<ByteVar>(PATH_MAX)
    val result = realpath(path, buf) ?: return null
    result.toKString()
}
