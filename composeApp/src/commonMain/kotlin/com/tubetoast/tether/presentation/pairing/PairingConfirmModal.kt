package com.tubetoast.tether.presentation.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.Button
import com.tubetoast.tether.ui.designsystem.ButtonVariant
import com.tubetoast.tether.ui.designsystem.LabelText
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme
import compose.icons.TablerIcons
import compose.icons.tablericons.Key

@Composable
fun PairingConfirmModal(
    pin: String,
    peerName: String,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val shapes = TetherTheme.shapes
    val typography = TetherTheme.typography

    Box(
        modifier = modifier.fillMaxSize().background(colors.surface.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.85f)
                .clip(shapes.md)
                .background(colors.surfaceRaised)
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = rememberVectorPainter(TablerIcons.Key),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.accent),
                modifier = Modifier.size(32.dp),
            )
            TitleText(text = "Confirm this is the same device")
            BodyText(
                text = "Both devices should show the same code.",
                color = colors.textMuted,
            )
            BasicText(
                text = pin,
                style = typography.titleLarge.copy(
                    fontSize = 48.sp,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                    color = colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            LabelText(text = peerName)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Button(
                    label = "Reject",
                    onClick = onReject,
                    contentDescription = "Reject pairing with $peerName",
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    label = "Confirm",
                    onClick = onConfirm,
                    contentDescription = "Confirm pairing with $peerName",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(name = "PairingConfirmModal")
@Composable
private fun PreviewPairingConfirmModal(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PairingConfirmModal(
            pin = "4729",
            peerName = "Artem's MacBook",
            onConfirm = {},
            onReject = {},
        )
    }
