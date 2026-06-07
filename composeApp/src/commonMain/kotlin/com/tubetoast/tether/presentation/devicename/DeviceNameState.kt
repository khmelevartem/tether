package com.tubetoast.tether.presentation.devicename

sealed interface DeviceNameState {
    data class Display(
        val name: String,
    ) : DeviceNameState

    data class Editing(
        val draft: String,
        val error: DeviceNameError?,
    ) : DeviceNameState
}

enum class DeviceNameError {
    EmptyName,
    TooLong,
    SaveFailed,
}
