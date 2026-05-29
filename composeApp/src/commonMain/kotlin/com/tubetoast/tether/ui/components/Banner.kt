package com.tubetoast.tether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherColors
import com.tubetoast.tether.ui.theme.TetherTheme

enum class BannerSeverity { Info, Warning, Error, Success }

@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    severity: BannerSeverity = BannerSeverity.Info,
    action: @Composable RowScope.() -> Unit = {},
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing

    val backgroundColor = bannerBackgroundColor(severity, colors)
    val liveRegionMode = when (severity) {
        BannerSeverity.Success -> LiveRegionMode.Polite
        else -> LiveRegionMode.Assertive
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .semantics { liveRegion = liveRegionMode },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BodyText(text = text, modifier = Modifier.weight(1f))
        action()
    }
}

private fun bannerBackgroundColor(severity: BannerSeverity, colors: TetherColors): Color =
    when (severity) {
        BannerSeverity.Info -> colors.accent.copy(alpha = 0.10f)
        BannerSeverity.Warning -> colors.peerIdentity.copy(alpha = 0.12f)
        BannerSeverity.Error -> colors.error.copy(alpha = 0.12f)
        BannerSeverity.Success -> colors.accent.copy(alpha = 0.08f)
    }

@Preview(name = "Banner — Info")
@Composable
private fun PreviewBannerInfo(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Banner(text = "Ready to send 3 files. Pick a device below.", severity = BannerSeverity.Info)
    }

@Preview(name = "Banner — Info with action")
@Composable
private fun PreviewBannerInfoWithAction(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Banner(
            text = "Ready to send 3 files. Pick a device below.",
            severity = BannerSeverity.Info,
        ) {
            TetherButton(label = "Cancel", onClick = {}, contentDescription = "Cancel")
        }
    }

@Preview(name = "Banner — Warning")
@Composable
private fun PreviewBannerWarning(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Banner(text = "Keep Tether open to complete the transfer.", severity = BannerSeverity.Warning)
    }

@Preview(name = "Banner — Error")
@Composable
private fun PreviewBannerError(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Banner(text = "Drop rejected — device offline.", severity = BannerSeverity.Error)
    }

@Preview(name = "Banner — Success")
@Composable
private fun PreviewBannerSuccess(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Banner(text = "Transfer complete.", severity = BannerSeverity.Success)
    }
