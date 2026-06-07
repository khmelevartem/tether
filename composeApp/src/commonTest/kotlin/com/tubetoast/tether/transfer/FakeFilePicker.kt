package com.tubetoast.tether.transfer

import kotlinx.coroutines.CompletableDeferred

internal class FakeFilePicker(
    var result: List<FileSource>,
    /** When non-null, pickFiles() suspends until this deferred completes. */
    private val gate: CompletableDeferred<Unit>? = null,
) : FilePicker {
    var pickFilesCalled = false
        private set
    var pickFolderCalled = false
        private set
    var pickPhotosCalled = false
        private set
    var pickFilesCallCount = 0
        private set

    override suspend fun pickFiles(): List<FileSource> {
        pickFilesCalled = true
        pickFilesCallCount++
        gate?.await()
        return result
    }

    override suspend fun pickFolder(): List<FileSource> {
        pickFolderCalled = true
        return result
    }

    override suspend fun pickPhotos(): List<FileSource> {
        pickPhotosCalled = true
        return result
    }
}
