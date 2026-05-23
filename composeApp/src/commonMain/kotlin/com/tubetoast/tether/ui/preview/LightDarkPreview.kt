package com.tubetoast.tether.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// weight(1f, fill = false) — each surface gets at most half the bounded viewport but stays smaller
// when its content is intrinsic-sized; otherwise fillMaxSize content in the first child eats the
// entire viewport and the dark half renders empty.
@Suppress("ktlint:compose:content-slot-reused")
@Composable
fun LightDarkPreview(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        PreviewSurface(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            darkTheme = false,
        ) { content() }
        PreviewSurface(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            darkTheme = true,
        ) { content() }
    }
}
