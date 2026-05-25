package com.tubetoast.tether.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.X

private val IconSize = 24.dp

@Composable
private fun TetherIcon(
    imageVector: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val painter = rememberVectorPainter(imageVector)
    Image(
        painter = painter,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        alpha = alpha,
        modifier = modifier.size(IconSize),
    )
}

@Composable
fun ChevronToggleIcon(
    expanded: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        TetherIcon(
            imageVector = if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
            tint = TetherTheme.colors.textMuted,
        )
    }
}

@Composable
fun InfoIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        TetherIcon(imageVector = TablerIcons.InfoCircle, tint = TetherTheme.colors.textMuted)
    }
}

@Composable
fun DismissCloseButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        TetherIcon(imageVector = TablerIcons.X, tint = TetherTheme.colors.textMuted)
    }
}

@Composable
fun RowCancelButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        TetherIcon(
            imageVector = TablerIcons.X,
            tint = TetherTheme.colors.error,
            alpha = if (enabled) 1f else 0.38f,
        )
    }
}

@Preview(name = "Icon buttons")
@Composable
private fun PreviewIconButtons(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Row {
            ChevronToggleIcon(expanded = false, onClick = {}, contentDescription = "Expand")
            ChevronToggleIcon(expanded = true, onClick = {}, contentDescription = "Collapse")
            InfoIconButton(onClick = {}, contentDescription = "Info")
            DismissCloseButton(onClick = {}, contentDescription = "Dismiss")
            RowCancelButton(onClick = {}, contentDescription = "Cancel")
            RowCancelButton(onClick = {}, contentDescription = "Cancel", enabled = false)
        }
    }
