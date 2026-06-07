package com.tubetoast.tether.presentation.devicename

import com.tubetoast.tether.config.DeviceNameViolation

sealed interface DeviceNameState {
    data class Display(
        val name: String,
    ) : DeviceNameState

    data class Editing(
        val draft: String,
        val violation: DeviceNameViolation?,
        val saveFailed: Boolean,
    ) : DeviceNameState
}
