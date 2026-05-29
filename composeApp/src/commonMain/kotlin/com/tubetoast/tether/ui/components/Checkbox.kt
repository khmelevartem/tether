package com.tubetoast.tether.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import compose.icons.tablericons.Check

private val CheckboxSize = 18.dp
private val CheckmarkSize = 12.dp

/**
 * A labelled checkbox row.
 *
 * The entire row is one focusable unit with [Role.Checkbox] semantics.
 * Colour alone does not convey state — the checkmark icon is always present
 * when checked.
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .tetherMinTouchTarget()
            .semantics {
                role = Role.Checkbox
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        CheckboxBox(checked = checked)
        BodyText(text = label)
    }
}

@Composable
internal fun CheckboxBox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val shapes = TetherTheme.shapes
    val spacing = TetherTheme.spacing

    Box(
        modifier = modifier
            .size(CheckboxSize)
            .clip(shapes.sm)
            .background(if (checked) colors.accent else colors.surface)
            .border(spacing.borderWidth, if (checked) colors.accent else colors.border, shapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Image(
                painter = rememberVectorPainter(TablerIcons.Check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.surfaceRaised),
                modifier = Modifier.size(CheckmarkSize),
            )
        }
    }
}

@Preview(name = "Checkbox — unchecked")
@Composable
private fun PreviewCheckboxUnchecked(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Checkbox(checked = false, onCheckedChange = {}, label = "Don't show again")
    }

@Preview(name = "Checkbox — checked")
@Composable
private fun PreviewCheckboxChecked(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Checkbox(checked = true, onCheckedChange = {}, label = "Don't show again")
    }
