package com.tubetoast.tether.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget
import compose.icons.TablerIcons
import compose.icons.tablericons.ToggleLeft
import compose.icons.tablericons.ToggleRight

private val ToggleIconSize = 28.dp

@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TetherTheme.colors

    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        val icon = if (checked) TablerIcons.ToggleRight else TablerIcons.ToggleLeft
        val tint = when {
            !enabled -> colors.textMuted
            checked -> colors.accent
            else -> colors.textMuted
        }
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(ToggleIconSize),
        )
    }
}

@Preview(name = "Toggle — off")
@Composable
private fun PreviewToggleOff(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Toggle(checked = false, onCheckedChange = {}, contentDescription = "Auto-send, currently Off")
    }

@Preview(name = "Toggle — on")
@Composable
private fun PreviewToggleOn(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Toggle(checked = true, onCheckedChange = {}, contentDescription = "Auto-send, currently On")
    }

@Preview(name = "Toggle — on disabled")
@Composable
private fun PreviewToggleOnDisabled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Toggle(checked = true, onCheckedChange = {}, contentDescription = "Auto-send, disabled", enabled = false)
    }

@Preview(name = "Toggle — off disabled")
@Composable
private fun PreviewToggleOffDisabled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Toggle(checked = false, onCheckedChange = {}, contentDescription = "Auto-send, disabled", enabled = false)
    }
