package com.tubetoast.tether.presentation.banners

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.components.Banner
import com.tubetoast.tether.ui.components.BannerSeverity
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes

@Composable
fun ForegroundConstraintBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Banner(
        text = "Keep Tether open to complete the transfer.",
        severity = BannerSeverity.Info,
        modifier = modifier,
    )
}

@Preview(name = "ForegroundConstraintBanner — visible")
@Composable
private fun PreviewVisible(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ForegroundConstraintBanner(visible = true)
    }
