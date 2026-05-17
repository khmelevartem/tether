package com.tubetoast.tether.presentation.transfer

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.components.BrandMark
import com.tubetoast.tether.ui.components.BrandMarkState
import com.tubetoast.tether.ui.components.ByteProgressRow
import com.tubetoast.tether.ui.components.CancelTextButton
import com.tubetoast.tether.ui.components.CurrentFileLabel
import com.tubetoast.tether.ui.components.PeerIdentityChip
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun TransferProgressScreen(
    state: TransferState,
    peerName: String,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onFolderConfirm: (Boolean) -> Unit,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
            ) {
                PeerIdentityChip(
                    name = peerName,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Center content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
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

                    is TransferState.PeerDropped -> BrandMarkState.Error(state.ratio)
                    is TransferState.ConnectionLost -> BrandMarkState.Error(state.ratio)
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

                BrandMark(
                    state = markState,
                    modifier = Modifier
                        .size(160.dp, 40.dp)
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

                    is TransferState.PeerDropped -> {
                        BasicText(
                            text = "${state.peer.name} is no longer reachable.",
                            style = typography.bodyMedium.copy(color = colors.textMuted),
                        )
                    }

                    is TransferState.ConnectionLost -> {
                        BasicText(
                            text = "Connection lost. Try again when you're back on Wi-Fi.",
                            style = typography.bodyMedium.copy(color = colors.textMuted),
                        )
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

            // Bottom actions
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

                    is TransferState.PeerDropped, is TransferState.ConnectionLost -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CancelTextButton(label = "Retry", onClick = onRetryAll)
                            CancelTextButton(label = "Done", onClick = onBack)
                        }
                    }

                    is TransferState.Terminal.Cancelled -> {
                        CancelTextButton(label = "Done", onClick = onBack)
                    }

                    else -> {}
                }
            }
        }

        if (state is TransferState.CancelConfirm) {
            CancelConfirmDialog(
                onStopTransfer = { /* handled by component */ },
                onKeepSending = { /* handled by component */ },
            )
        }
    }
}
