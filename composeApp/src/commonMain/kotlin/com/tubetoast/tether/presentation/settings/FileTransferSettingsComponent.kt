package com.tubetoast.tether.presentation.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.preferences.FileTransferPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

/** Hosted by the Settings surface (#222). */
class FileTransferSettingsComponent(
    componentContext: ComponentContext,
    private val preferences: FileTransferPreferences,
    defaultSaveLocation: String = "",
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val scope = coroutineScope

    private val _state = MutableValue(FileTransferSettingsState.initial(defaultSaveLocation))
    val state: Value<FileTransferSettingsState> = _state

    init {
        combine(
            preferences.observeSaveLocation(),
            preferences.observeLargeSelectionWarning(),
            preferences.observeSaveToGallery(),
        ) { location, warning, gallery ->
            _state.update {
                it.copy(
                    saveLocation = location,
                    largeSelectionWarning = warning,
                    saveToGallery = gallery,
                )
            }
        }.launchIn(scope)
    }

    fun onSetLargeSelectionWarning(enabled: Boolean) {
        scope.launch { preferences.setLargeSelectionWarning(enabled) }
    }

    fun onSetSaveToGallery(enabled: Boolean) {
        scope.launch { preferences.setSaveToGallery(enabled) }
    }
}
