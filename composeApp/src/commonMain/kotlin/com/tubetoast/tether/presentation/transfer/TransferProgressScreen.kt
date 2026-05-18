package com.tubetoast.tether.presentation.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.tubetoast.tether.ui.components.BrandMark
import com.tubetoast.tether.ui.components.BrandMarkDefaults
import com.tubetoast.tether.ui.components.BrandMarkState
import com.tubetoast.tether.ui.components.ByteProgressRow
import com.tubetoast.tether.ui.components.CancelTextButton
import com.tubetoast.tether.ui.components.CurrentFileLabel
import com.tubetoast.tether.ui.components.PeerIdentityChip
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget

@Composable
fun TransferProgressScreen(
    state: TransferState,
    peerName: String,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProgressTopBar(peerName = peerName, onBack = onBack)
            ProgressCenterContent(state = state, peerName = peerName, modifier = Modifier.weight(1f))
            ProgressBottomActions(state = state, onCancel = onCancel, onRetryAll = onRetryAll, onBack = onBack)
        }
    }
}

@Composable
private fun ProgressTopBar(peerName: String, onBack: () -> Unit) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        BasicText(
            text = "←",
            style = typography.titleMedium.copy(color = colors.accent),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .tetherMinTouchTarget()
                .clickable(onClick = onBack)
                .padding(spacing.sm)
                .semantics {
                    contentDescription = "Back"
                    role = Role.Button
                },
        )
        PeerIdentityChip(
            name = peerName,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ProgressCenterContent(state: TransferState, peerName: String, modifier: Modifier = Modifier) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing

    val markState = when (state) {
        is TransferState.Preparing -> BrandMarkState.Searching
        is TransferState.InProgress -> {
            val total = state.bytesTotal
            val ratio = if (total != null && total > 0) {
                state.bytesDone.toFloat() / total.toFloat()
            } else {
                0f
            }
            BrandMarkState.Progress(ratio)
        }
        else -> BrandMarkState.Idle
    }
    val markA11y = when (state) {
        is TransferState.InProgress -> {
            val total = state.bytesTotal
            val pct = if (total != null && total > 0) {
                (state.bytesDone * 100 / total).toString()
            } else {
                "0"
            }
            "Transfer progress: $pct% complete"
        }
        else -> "Transfer status indicator"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(
            state = markState,
            modifier = Modifier
                // 2× default size for use as primary surface element.
                .size(BrandMarkDefaults.DefaultWidth * 2, BrandMarkDefaults.DefaultHeight * 2)
                .semantics { contentDescription = markA11y },
        )
        Spacer(modifier = Modifier.height(spacing.xl))
        when (state) {
            is TransferState.Preparing -> {
                BasicText(
                    text = "Connecting to $peerName…",
                    style = typography.bodyMedium.copy(color = colors.textMuted),
                )
            }
            is TransferState.InProgress -> {
                CurrentFileLabel(fileName = state.currentFile)
                Spacer(modifier = Modifier.height(spacing.xs))
                ByteProgressRow(
                    bytesDone = state.bytesDone,
                    bytesTotal = state.bytesTotal,
                    speedBytesPerSec = state.speedBytesPerSec,
                )
                state.inlineNotice?.let { notice ->
                    Spacer(modifier = Modifier.height(spacing.sm))
                    BasicText(
                        text = notice,
                        style = typography.bodyMedium.copy(color = colors.error),
                    )
                }
            }
            is TransferState.Terminal.Cancelled -> {
                BasicText(
                    text = "Cancelled.",
                    style = typography.bodyMedium.copy(color = colors.textMuted),
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun ProgressBottomActions(
    state: TransferState,
    onCancel: () -> Unit,
    onRetryAll: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = TetherTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is TransferState.InProgress, is TransferState.Preparing -> {
                CancelTextButton(onClick = onCancel)
            }
            is TransferState.Terminal.Cancelled -> {
                CancelTextButton(label = "Done", onClick = onBack)
            }
            else -> {}
        }
    }
}
