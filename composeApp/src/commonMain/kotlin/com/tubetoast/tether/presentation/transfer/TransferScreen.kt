package com.tubetoast.tether.presentation.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun TransferScreen(
    component: TransferComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val current = state

    when (current) {
        is TransferState.FolderConfirm -> FolderSendConfirmDialog(
            fileCount = current.fileCount,
            totalBytes = current.totalBytes,
            onContinue = { component.onFolderConfirm(true) },
            onCancel = { component.onFolderConfirm(false) },
        )

        is TransferState.CancelConfirm -> {
            TransferProgressScreen(
                state = current.snapshot,
                peerName = component.peer.name,
                onCancel = component::onCancelClicked,
                onBack = component::onDone,
                onRetryAll = component::onRetryAll,
                modifier = modifier,
            )
            CancelConfirmDialog(
                onStopTransfer = component::onCancelConfirmed,
                onKeepSending = component::onKeepSending,
            )
        }

        is TransferState.Terminal.Cancelled -> TransferProgressScreen(
            state = current,
            peerName = component.peer.name,
            onCancel = component::onCancelClicked,
            onBack = component::onDone,
            onRetryAll = component::onRetryAll,
            modifier = modifier,
        )

        is TransferState.Terminal -> TransferSummaryScreen(
            state = current,
            onDone = component::onDone,
            onRetryFile = component::onRetryFile,
            onRetryAll = component::onRetryAll,
            onBack = component::onDone,
            modifier = modifier,
        )

        else -> TransferProgressScreen(
            state = current,
            peerName = component.peer.name,
            onCancel = component::onCancelClicked,
            onBack = component::onDone,
            onRetryAll = component::onRetryAll,
            modifier = modifier,
        )
    }
}
