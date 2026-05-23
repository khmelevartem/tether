package com.tubetoast.tether.ui.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tubetoast.tether.protocol.Device

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
}

@Composable
fun PreviewSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(modifier = modifier, content = content)
    }
}
