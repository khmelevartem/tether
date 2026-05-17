package com.tubetoast.tether.presentation.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.components.BrandMark
import com.tubetoast.tether.ui.components.BrandMarkState
import com.tubetoast.tether.ui.components.CancelTextButton
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun TransferSummaryScreen(
    state: TransferState.Terminal,
    onDone: () -> Unit,
    onRetryFile: (String) -> Unit,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onDone,
) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
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
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onBack)
                    .padding(spacing.sm)
                    .semantics {
                        contentDescription = "Back"
                        role = Role.Button
                    },
            )
            BasicText(
                text = "Transfer complete",
                style = typography.titleMedium.copy(color = colors.textPrimary),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val markState: BrandMarkState
        val markA11y: String
        val summaryText: String
        val failed: List<FailedFile>
        val showRetryAll: Boolean

        when (state) {
            is TransferState.Terminal.AllSuccess -> {
                markState = BrandMarkState.Success
                markA11y = "Transfer succeeded"
                summaryText = if (state.count == 1) {
                    "Sent 1 file to ${state.peer.name}."
                } else {
                    "Sent ${state.count} files to ${state.peer.name}."
                }
                failed = emptyList()
                showRetryAll = false
            }

            is TransferState.Terminal.PartialFailure -> {
                markState = BrandMarkState.Success
                markA11y = "Transfer succeeded"
                summaryText =
                    "Sent ${state.sent} of ${state.total} files to ${state.peer.name}. ${state.failed.size} failed:"
                failed = state.failed
                showRetryAll = false
            }

            is TransferState.Terminal.AllFailed -> {
                markState = BrandMarkState.Error(0f)
                markA11y = "Transfer failed"
                summaryText = "Couldn't send any files to ${state.peer.name}."
                failed = state.failed
                showRetryAll = false
            }

            is TransferState.Terminal.ConnectionErrorSummary -> {
                markState = BrandMarkState.Error(state.sent.toFloat() / state.total.coerceAtLeast(1).toFloat())
                markA11y = "Transfer failed"
                summaryText =
                    "Sent ${state.sent} of ${state.total} files to ${state.peer.name}. ${state.failed.size} failed:"
                failed = state.failed
                showRetryAll = true
            }

            TransferState.Terminal.Cancelled -> error("Cancelled routes to TransferProgressScreen")
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(modifier = Modifier.height(spacing.xxl))
                BrandMark(
                    state = markState,
                    modifier = Modifier
                        .size(160.dp, 40.dp)
                        .semantics { contentDescription = markA11y },
                )
                Spacer(modifier = Modifier.height(spacing.xl))
                BasicText(
                    text = summaryText,
                    style = typography.titleMedium.copy(color = colors.textPrimary),
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
                Spacer(modifier = Modifier.height(spacing.lg))
            }

            items(failed) { failedFile ->
                FailedFileRow(
                    failedFile = failedFile,
                    onRetry = { onRetryFile(failedFile.name) },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showRetryAll) {
                CancelTextButton(label = "Retry all", onClick = onRetryAll)
                Spacer(modifier = Modifier.height(spacing.sm))
            }
            CancelTextButton(label = "Done", onClick = onDone)
        }
    }
}

@Composable
private fun FailedFileRow(
    failedFile: FailedFile,
    onRetry: () -> Unit,
) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing
    val reasonLabel = when (failedFile.reason) {
        FailureReason.Unreadable -> "Unreadable"
        FailureReason.ReceiverWriteFailed -> "Couldn't save"
        FailureReason.ConnectionLost -> "Connection lost"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = failedFile.name,
                style = typography.bodyMedium.copy(color = colors.textPrimary),
                maxLines = 1,
            )
            BasicText(
                text = reasonLabel,
                style = typography.labelSmall.copy(color = colors.textMuted),
            )
        }
        BasicText(
            text = "Retry",
            style = typography.bodyMedium.copy(color = colors.accent),
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(spacing.sm)
                .semantics {
                    role = Role.Button
                    contentDescription = "Retry sending ${failedFile.name}"
                },
        )
    }
}
