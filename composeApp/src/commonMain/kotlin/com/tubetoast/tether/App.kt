package com.tubetoast.tether

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tubetoast.tether.presentation.DeviceListComponent
import com.tubetoast.tether.presentation.DeviceListScreen

@Composable
fun App(component: DeviceListComponent, modifier: Modifier = Modifier) {
    MaterialTheme {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .safeContentPadding(),
        ) {
            DeviceListScreen(component)
        }
    }
}
