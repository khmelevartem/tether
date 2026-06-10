package com.tubetoast.tether.presentation.devicename

sealed interface DeviceNameState {
    data class Display(
        val name: String,
    ) : DeviceNameState

    data class Editing(
        val draft: String,
        val errorMessage: String?,
        val confirmEnabled: Boolean,
    ) : DeviceNameState
}
