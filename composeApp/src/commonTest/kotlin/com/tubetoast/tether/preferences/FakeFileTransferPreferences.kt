package com.tubetoast.tether.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFileTransferPreferences(
    largeSelectionWarning: Boolean = true,
    saveToGallery: Boolean = true,
) : FileTransferPreferences {
    private val largeSelectionWarningFlow = MutableStateFlow(largeSelectionWarning)
    private val saveLocationFlow = MutableStateFlow("")
    private val saveToGalleryFlow = MutableStateFlow(saveToGallery)

    override fun observeLargeSelectionWarning(): Flow<Boolean> = largeSelectionWarningFlow

    override suspend fun setLargeSelectionWarning(enabled: Boolean) {
        largeSelectionWarningFlow.value = enabled
    }

    override fun observeSaveLocation(): Flow<String> = saveLocationFlow

    override suspend fun setSaveLocation(path: String) {
        saveLocationFlow.value = path
    }

    override fun observeSaveToGallery(): Flow<Boolean> = saveToGalleryFlow

    override suspend fun setSaveToGallery(enabled: Boolean) {
        saveToGalleryFlow.value = enabled
    }
}
