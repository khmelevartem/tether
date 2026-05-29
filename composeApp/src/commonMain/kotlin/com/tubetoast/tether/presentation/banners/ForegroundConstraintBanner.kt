package com.tubetoast.tether.presentation.banners

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.components.BodyText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun ForegroundConstraintBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing

    BodyText(
        text = "Keep Tether open to complete the transfer.",
        modifier = modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.15f))
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
            },
    )
}

@Preview(name = "ForegroundConstraintBanner — visible")
@Composable
private fun PreviewVisible(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ForegroundConstraintBanner(visible = true)
    }
