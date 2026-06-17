package com.tubetoast.tether.presentation.settings

data class FileTransferSettingsState(
    val saveLocation: String,
    val largeSelectionWarning: Boolean,
    val saveToGallery: Boolean,
    val showGalleryToggle: Boolean,
) {
    companion object {
        fun initial(
            defaultSaveLocation: String = "",
            showGalleryToggle: Boolean = false,
        ) = FileTransferSettingsState(
            saveLocation = defaultSaveLocation,
            largeSelectionWarning = true,
            saveToGallery = true,
            showGalleryToggle = showGalleryToggle,
        )
    }
}
