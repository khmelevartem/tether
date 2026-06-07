package com.tubetoast.tether.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tubetoast.tether.presentation.devicename.DeviceNameState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.theme.TetherTheme

object PreviewFixtures {
    val emptyDevices: List<Device> = emptyList()

    val singleDevice: List<Device> = listOf(
        Device(name = "Alice's Phone", host = "192.168.1.42", port = 7070),
    )

    val multipleDevices: List<Device> = listOf(
        Device(name = "Alice's Phone", host = "192.168.1.42", port = 7070),
        Device(name = "Bob's Laptop", host = "192.168.1.55", port = 7070),
        Device(name = "Carol's iPad", host = "192.168.1.88", port = 7070),
    )

    object DeviceName {
        val display = DeviceNameState.Display("Alice's MacBook")
        val editing = DeviceNameState.Editing(draft = "Alice's MacBook", errorMessage = null, confirmEnabled = true)
        val editingEmptyError = DeviceNameState.Editing(
            draft = "",
            errorMessage = "Enter a name.",
            confirmEnabled = false,
        )
        val editingTooLongError = DeviceNameState.Editing(
            draft = "A name that is too long and exceeds the fifty codepoint limit",
            errorMessage = "Use 50 characters or fewer.",
            confirmEnabled = false,
        )
        val editingSaveFailed = DeviceNameState.Editing(
            draft = "Alice's MacBook",
            errorMessage = "Couldn't save the name. Try again.",
            confirmEnabled = true,
        )
    }
}

@Composable
fun PreviewSurface(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    TetherTheme(darkTheme = darkTheme) {
        Box(modifier = modifier.background(TetherTheme.colors.surface), content = { content() })
    }
}
