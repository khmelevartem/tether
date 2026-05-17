package com.tubetoast.tether.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun CurrentFileLabel(
    fileName: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = fileName,
        style = TetherTheme.typography.bodyMedium.copy(color = TetherTheme.colors.textMuted),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = fileName },
    )
}
