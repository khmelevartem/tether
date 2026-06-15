package com.tubetoast.tether.presentation.settings

data class FileTransferSettingsState(
    val saveLocation: String,
    val largeSelectionWarning: Boolean,
    val saveToGallery: Boolean,
) {
    companion object {
        fun initial(defaultSaveLocation: String = "") = FileTransferSettingsState(
            saveLocation = defaultSaveLocation,
            largeSelectionWarning = true,
            saveToGallery = true,
        )
    }
}
