package com.tubetoast.tether.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun LightDarkPreview(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val movable = remember(content) { movableContentOf(content) }
    Column(modifier = modifier) {
        PreviewSurface(darkTheme = false) { movable() }
        PreviewSurface(darkTheme = true) { movable() }
    }
}
