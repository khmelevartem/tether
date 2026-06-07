package com.tubetoast.tether.presentation.devicename

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.config.DEVICE_NAME_MAX_CODEPOINTS
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.deviceNameCodepointCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceNameComponent(
    componentContext: ComponentContext,
    private val nameStore: DeviceNameStore,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val scope = coroutineScope

    private val _state = MutableStateFlow<DeviceNameState>(DeviceNameState.Display(""))
    val state: StateFlow<DeviceNameState> = _state

    private var committedName = ""

    init {
        nameStore.name
            .onEach { name ->
                committedName = name
                _state.update { current ->
                    if (current is DeviceNameState.Display) DeviceNameState.Display(name) else current
                }
            }.launchIn(scope)
    }

    fun onEditClick() {
        val current = _state.value
        if (current is DeviceNameState.Display) {
            _state.update { DeviceNameState.Editing(draft = current.name, error = null) }
        }
    }

    fun onDraftChange(text: String) {
        _state.update { current ->
            if (current is DeviceNameState.Editing) {
                current.copy(draft = text, error = liveError(text))
            } else {
                current
            }
        }
    }

    fun onConfirm() {
        val current = _state.value as? DeviceNameState.Editing ?: return
        val error = liveError(current.draft)
        if (error != null) {
            _state.update { current.copy(error = error) }
            return
        }
        scope.launch {
            val result = nameStore.setName(current.draft)
            result.fold(
                onSuccess = { saved ->
                    _state.update { DeviceNameState.Display(saved) }
                },
                onFailure = {
                    _state.update {
                        (_state.value as? DeviceNameState.Editing)?.copy(error = DeviceNameError.SaveFailed)
                            ?: current.copy(error = DeviceNameError.SaveFailed)
                    }
                },
            )
        }
    }

    fun onCancel() {
        _state.update { DeviceNameState.Display(committedName) }
    }

    private fun liveError(draft: String): DeviceNameError? {
        val trimmed = draft.trim()
        return when {
            trimmed.isEmpty() -> DeviceNameError.EmptyName
            deviceNameCodepointCount(trimmed) > DEVICE_NAME_MAX_CODEPOINTS -> DeviceNameError.TooLong
            else -> null
        }
    }
}
