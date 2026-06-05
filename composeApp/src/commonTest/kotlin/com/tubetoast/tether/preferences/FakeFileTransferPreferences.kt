package com.tubetoast.tether.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFileTransferPreferences(
    largeSelectionWarning: Boolean = true,
) : FileTransferPreferences {
    private val largeSelectionWarningFlow = MutableStateFlow(largeSelectionWarning)
    private val saveLocationFlow = MutableStateFlow("")

    override fun observeLargeSelectionWarning(): Flow<Boolean> = largeSelectionWarningFlow

    override suspend fun setLargeSelectionWarning(enabled: Boolean) {
        largeSelectionWarningFlow.value = enabled
    }

    override fun observeSaveLocation(): Flow<String> = saveLocationFlow

    override suspend fun setSaveLocation(path: String) {
        saveLocationFlow.value = path
    }
}
