package com.tubetoast.tether.presentation.devicename

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.DeviceNameValidator
import com.tubetoast.tether.config.DeviceNameViolation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val ERROR_EMPTY = "Enter a name."
private const val ERROR_TOO_LONG = "Use 50 characters or fewer."
private const val ERROR_SAVE_FAILED = "Couldn't save the name. Try again."

private fun violationMessage(violation: DeviceNameViolation): String = when (violation) {
    DeviceNameViolation.Empty -> ERROR_EMPTY
    DeviceNameViolation.TooLong -> ERROR_TOO_LONG
}

class DeviceNameComponent(
    componentContext: ComponentContext,
    private val nameStore: DeviceNameStore,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val scope = coroutineScope

    private val _state = MutableValue<DeviceNameState>(DeviceNameState.Display(""))
    val state: Value<DeviceNameState> = _state

    init {
        nameStore.name
            .onEach { name ->
                _state.update { current ->
                    if (current is DeviceNameState.Display) DeviceNameState.Display(name) else current
                }
            }.launchIn(scope)
    }

    fun onEditClick() {
        _state.update { current ->
            if (current is DeviceNameState.Display) {
                DeviceNameState.Editing(draft = current.name, errorMessage = null, confirmEnabled = true)
            } else {
                current
            }
        }
    }

    fun onDraftChange(text: String) {
        _state.update { current ->
            if (current is DeviceNameState.Editing) {
                val violation = DeviceNameValidator.violationOf(text)
                current.copy(
                    draft = text,
                    errorMessage = violation?.let { violationMessage(it) },
                    confirmEnabled = violation == null,
                )
            } else {
                current
            }
        }
    }

    fun onConfirm() {
        val current = _state.value as? DeviceNameState.Editing ?: return
        val violation = DeviceNameValidator.violationOf(current.draft)
        if (violation != null) {
            _state.update {
                (it as? DeviceNameState.Editing)?.copy(
                    errorMessage = violationMessage(violation),
                    confirmEnabled = false,
                )
                    ?: it
            }
            return
        }
        scope.launch {
            val editingAtStart = current
            val result = nameStore.setName(current.draft)
            result.fold(
                onSuccess = { saved ->
                    _state.update { if (it === editingAtStart) DeviceNameState.Display(saved) else it }
                },
                onFailure = {
                    _state.update {
                        (it as? DeviceNameState.Editing)?.copy(errorMessage = ERROR_SAVE_FAILED, confirmEnabled = true)
                            ?: it
                    }
                },
            )
        }
    }

    fun onCancel() {
        _state.update { DeviceNameState.Display(nameStore.currentName) }
    }
}
