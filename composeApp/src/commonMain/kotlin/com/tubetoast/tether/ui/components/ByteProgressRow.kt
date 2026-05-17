package com.tubetoast.tether.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tubetoast.tether.presentation.transfer.formatProgress
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun ByteProgressRow(
    bytesDone: Long,
    bytesTotal: Long?,
    speedBytesPerSec: Long,
    modifier: Modifier = Modifier,
) {
    val text = formatProgress(bytesDone, bytesTotal, speedBytesPerSec)
    val a11yLabel = buildA11yLabel(bytesDone, bytesTotal, speedBytesPerSec, text)
    BasicText(
        text = text,
        style = TetherTheme.typography.numeric.copy(color = TetherTheme.colors.textMuted),
        modifier = modifier.semantics { contentDescription = a11yLabel },
    )
}

private fun buildA11yLabel(
    bytesDone: Long,
    bytesTotal: Long?,
    speedBytesPerSec: Long,
    display: String,
): String = if (bytesTotal != null) {
    "$bytesDone bytes of $bytesTotal bytes transferred, current speed $speedBytesPerSec bytes per second"
} else {
    "$bytesDone bytes transferred, current speed $speedBytesPerSec bytes per second"
}
