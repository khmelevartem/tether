package com.tubetoast.tether.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme
import compose.icons.TablerIcons
import compose.icons.tablericons.CloudUpload

@Composable
internal fun DragOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(TetherTheme.colors.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TetherTheme.spacing.md),
            modifier = Modifier
                .wrapContentSize()
                .padding(TetherTheme.spacing.xl),
        ) {
            Image(
                painter = rememberVectorPainter(TablerIcons.CloudUpload),
                contentDescription = null,
                colorFilter = ColorFilter.tint(TetherTheme.colors.accent),
                modifier = Modifier.size(48.dp),
            )
            TitleText(text = "Drop files to send")
        }
    }
}

@Preview(name = "DragOverlay")
@Composable
private fun PreviewDragOverlay(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        DragOverlay(Modifier.size(400.dp, 600.dp))
    }
