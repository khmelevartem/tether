package com.tubetoast.tether.transfer

internal class FakeFilePicker(
    var result: List<FileSource>,
) : FilePicker {
    var pickFilesCalled = false
        private set
    var pickFolderCalled = false
        private set
    var pickPhotosCalled = false
        private set

    override suspend fun pickFiles(): List<FileSource> {
        pickFilesCalled = true
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
